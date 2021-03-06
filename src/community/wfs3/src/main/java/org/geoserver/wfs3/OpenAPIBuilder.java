package org.geoserver.wfs3;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import org.geoserver.ManifestLoader;
import org.geoserver.config.ContactInfo;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.ServiceException;
import org.geoserver.util.IOUtils;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geoserver.wfs3.response.CollectionsDocument;
import org.geoserver.wfs3.response.ConformanceDocument;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenAPIBuilder {

    static final String OPENAPI_SPECIFICATION;

    static {
        try (InputStream is = OpenAPIBuilder.class.getResourceAsStream("openapi.yaml")) {
            OPENAPI_SPECIFICATION = IOUtils.toString(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read the openapi.yaml template", e);
        }
    }

    public OpenAPI build(BaseRequest request, WFSInfo wfs) {
        OpenAPI api = readTemplate();

        // build "info"
        ContactInfo contactInfo = wfs.getGeoServer().getGlobal().getSettings().getContact();
        Contact contact =
                new Contact()
                        .email(contactInfo.getContactEmail())
                        .name(
                                Stream.of(
                                                contactInfo.getContactPerson(),
                                                contactInfo.getContactOrganization())
                                        .filter(s -> s != null)
                                        .collect(Collectors.joining(" - ")))
                        .url(contactInfo.getOnlineResource());
        String title = wfs.getTitle() == null ? "WFS 3.0 server" : wfs.getTitle();
        String version = getGeoServerVersion();
        Info info =
                new Info()
                        .contact(contact)
                        .title(title)
                        .description(wfs.getAbstract())
                        .version(version);
        api.info(info);

        // the external documentation
        api.externalDocs(
                new ExternalDocumentation()
                        .description("WFS specification")
                        .url("https://github.com/opengeospatial/WFS_FES"));

        // the servers
        String wfsUrl =
                ResponseUtils.buildURL(
                        request.getBaseUrl(), "wfs3", null, URLMangler.URLType.SERVICE);
        api.servers(Arrays.asList(new Server().description("This server").url(wfsUrl)));

        // adjust path output formats
        declareGetResponseFormats(api, "/", OpenAPI.class);
        declareGetResponseFormats(api, "/conformance", ConformanceDocument.class);
        declareGetResponseFormats(api, "/collections", CollectionsDocument.class);
        declareGetResponseFormats(api, "/collections/{collectionId}", CollectionsDocument.class);
        declareGetResponseFormats(
                api, "/collections/{collectionId}/items", FeatureCollectionResponse.class);
        declareGetResponseFormats(
                api,
                "/collections/{collectionId}/items/{featureId}",
                FeatureCollectionResponse.class);

        return api;
    }

    private void declareGetResponseFormats(OpenAPI api, String path, Class<?> binding) {
        PathItem pi = api.getPaths().get(path);
        Operation get = pi.getGet();
        Content content = get.getResponses().get("200").getContent();
        List<String> formats = DefaultWebFeatureService30.getAvailableFormats(binding);
        // first remove the ones missing
        Set<String> missingFormats = new HashSet<>(content.keySet());
        missingFormats.removeAll(formats);
        missingFormats.forEach(f -> content.remove(f));
        // then add the ones not already declared
        Set<String> extraFormats = new HashSet<>(formats);
        extraFormats.removeAll(content.keySet());
        for (String extraFormat : extraFormats) {
            MediaType mediaType = new MediaType();
            if (extraFormat.contains("yaml") && content.get("application/json") != null) {
                // same schema as JSON
                mediaType.schema(content.get("application/json").getSchema());
            } else if (extraFormat.contains("text")) {
                mediaType.schema(new StringSchema());
            } else {
                mediaType.schema(new BinarySchema());
            }
            content.addMediaType(extraFormat, mediaType);
        }
    }

    /**
     * Reads the template to customize (each time, as the object tree is not thread safe nor
     * cloneable not serializable)
     *
     * @return
     */
    private OpenAPI readTemplate() {
        try {
            return Yaml.mapper().readValue(OPENAPI_SPECIFICATION, OpenAPI.class);
        } catch (Exception e) {
            throw new ServiceException(e);
        }
    }

    private String getGeoServerVersion() {
        ManifestLoader.AboutModel versions = ManifestLoader.getVersions();
        TreeSet<ManifestLoader.AboutModel.ManifestModel> manifests = versions.getManifests();
        return manifests
                .stream()
                .filter(m -> m.getName().equalsIgnoreCase("GeoServer"))
                .map(m -> m.getEntries().get("Version"))
                .findFirst()
                .orElse("1.0.0");
    }
}
