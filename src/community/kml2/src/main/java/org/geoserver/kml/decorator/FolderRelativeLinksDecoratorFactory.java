/* Copyright (c) 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.kml.decorator;

import static org.geoserver.ows.util.ResponseUtils.appendPath;
import static org.geoserver.ows.util.ResponseUtils.buildURL;

import java.io.IOException;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.wms.GetMapRequest;
import org.geotools.data.FeatureSource;
import org.geotools.map.Layer;
import org.opengis.feature.type.Name;

import de.micromata.opengis.kml.v_2_2_0.Feature;
import de.micromata.opengis.kml.v_2_2_0.Folder;
import de.micromata.opengis.kml.v_2_2_0.Link;
import de.micromata.opengis.kml.v_2_2_0.NetworkLink;

/**
 * Encodes previous/next network links when paging is used
 * TODO: move this in GeoSearch, as it references its REST services 
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class FolderRelativeLinksDecoratorFactory implements KmlDecoratorFactory {

    @Override
    public KmlDecorator getDecorator(Class<? extends Feature> featureClass,
            KmlEncodingContext context) {
        // we decorate only the feature collection folders
        if(!(featureClass.equals(Folder.class))) {
            return null;
        }
        
        // see if we have to encode relative links
        GetMapRequest request = context.getRequest();
        String relLinks = (String) request.getFormatOptions().get("relLinks");
        // Add prev/next links if requested
        if(request.getMaxFeatures() != null && relLinks != null
                && relLinks.equalsIgnoreCase("true")) {
            return new FolderRelativeLinksDecorator();
        } else {
            return null;
        }
    }

    
    static class FolderRelativeLinksDecorator implements KmlDecorator {

        @Override
        public Feature decorate(Feature feature, KmlEncodingContext context) {
            // if not a layer link, move on
            if(context.getCurrentLayer() == null || context.getCurrentFeatureCollection() == null) {
                return feature;
            }
            
            Folder folder = (Folder) feature;
            
            String linkbase = "";
            try {
                linkbase = getFeatureTypeURL(context);
                linkbase += ".kml";
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            GetMapRequest request = context.getRequest();
            int maxFeatures = request.getMaxFeatures();
            int startIndex = (request.getStartIndex() == null) ? 0 : request.getStartIndex().intValue();
            int prevStart = startIndex - maxFeatures;
            int nextStart = startIndex + maxFeatures;
            
            // Previous page, if any
            if (prevStart >= 0) {
                encodeSequentialNetworkLink(folder, linkbase, prevStart, maxFeatures, "prev",
                        "Previous page");
            }

            // Next page, if potentially any
            if (context.getCurrentFeatureCollection().size() >= maxFeatures) {
                encodeSequentialNetworkLink(folder, linkbase, nextStart, maxFeatures, "next",
                        "Next page");
            }
            
            return folder;
        }
        
        private void encodeSequentialNetworkLink(Folder folder, String linkbase, int start, int maxFeatures, String id, String readableName) {
            NetworkLink nl = folder.createAndAddNetworkLink();
            Link link = nl.createAndSetLink();
            link.setHref(linkbase + "?startindex=" + start + "&maxfeatures=" + maxFeatures);
            nl.setDescription(readableName);
            nl.setId(id);
        }

        
        protected String getFeatureTypeURL(KmlEncodingContext context) throws IOException {
            GeoServer gs = context.getWms().getGeoServer();
            Catalog catalog = gs.getCatalog();
            Layer layer = context.getCurrentLayer();
            FeatureSource featureSource = layer.getFeatureSource();
            Name typeName = featureSource.getSchema().getName();
            String nsUri = typeName.getNamespaceURI();
            NamespaceInfo ns = catalog.getNamespaceByURI(nsUri);
            String featureTypeName = typeName.getLocalPart();
            GetMapRequest request = context.getRequest();
            String baseURL = request.getBaseUrl();
            String prefix = ns.getPrefix();
            return buildURL(baseURL, appendPath("rest", prefix, featureTypeName), null,
                    URLType.SERVICE);
        }
        
    }
}
