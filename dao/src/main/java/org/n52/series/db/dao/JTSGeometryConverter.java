package org.n52.series.db.dao;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to convert between VividSolutions ({@code com.vividsolutions.jts}) and LocationTech
 * ({@code org.locationtech.jts}) Geometries.
 *
 * @author Christian Autermann
 */
public final class JTSGeometryConverter {
    private static final String POINT = "Point";
    private static final String MULTI_POINT = "MultiPoint";
    private static final String LINE_STRING = "LineString";
    private static final String MULTI_LINE_STRING = "MultiLineString";
    private static final String MULTI_POLYGON = "MultiPolygon";
    private static final String POLYGON = "Polygon";
    private static final String GEOMETRY_COLLECTION = "GeometryCollection";
    private static final VS2LT VS2LT_CONVERTER = new VS2LT();
    private static final LT2VS LT2VS_CONVERTER = new LT2VS();

    /**
     * Private utility class constructor.
     */
    private JTSGeometryConverter() {
    }

    /**
     * Convert from old-style VividSolutions Geometries to new-style LocationTech Geometries.
     *
     * @param geometry the geometry
     *
     * @return the converted geometry
     */
    public static org.locationtech.jts.geom.Geometry convert(com.vividsolutions.jts.geom.Geometry geometry) {
        return VS2LT_CONVERTER.convert(geometry);
    }

    /**
     * Convert from new-style LocationTech Geometries to old-style VividSolutions Geometries.
     *
     * @param geometry the geometry
     *
     * @return the converted geometry
     */
    public static com.vividsolutions.jts.geom.Geometry convert(org.locationtech.jts.geom.Geometry geometry) {
        return LT2VS_CONVERTER.convert(geometry);
    }

    private static final class LT2VS {

        /**
         * Convert the supplied {@code Geometry}.
         *
         * @param geometry the geometry
         *
         * @return the converted geometry
         */
        com.vividsolutions.jts.geom.Geometry convert(org.locationtech.jts.geom.Geometry geometry) {
            if (geometry == null) {
                return null;
            }

            com.vividsolutions.jts.geom.GeometryFactory factory = convertFactory(geometry.getFactory());

            switch (geometry.getGeometryType()) {
                case POINT:
                    return factory.createPoint(convertCoordinate(geometry.getCoordinate()));
                case LINE_STRING:
                    return factory.createLineString(convertCoordinates(geometry.getCoordinates()));
                case POLYGON: {
                    org.locationtech.jts.geom.Polygon p = (org.locationtech.jts.geom.Polygon) geometry;
                    int n = p.getNumInteriorRing();
                    com.vividsolutions.jts.geom.LinearRing[] holes = new com.vividsolutions.jts.geom.LinearRing[n];
                    for (int i = 0; i < n; ++i) {
                        holes[i] = createLinearRing(factory, p.getInteriorRingN(i));
                    }
                    return factory.createPolygon(createLinearRing(factory, p.getExteriorRing()), holes);
                }
                case MULTI_POINT:
                case MULTI_LINE_STRING:
                case MULTI_POLYGON:
                case GEOMETRY_COLLECTION: {
                    int n = geometry.getNumGeometries();
                    List<com.vividsolutions.jts.geom.Geometry> array = new ArrayList<>(n);
                    for (int i = 0; i < n; ++i) {
                        array.add(convert(geometry.getGeometryN(i)));
                    }
                    return factory.buildGeometry(array);
                }
                default:
                    throw new IllegalArgumentException("Unsupported cv geometry: " + geometry);
            }

        }

        /**
         * Convert the supplied {@code GeometryFactory}.
         *
         * @param factory the factory
         *
         * @return the converted factory
         */
        private com.vividsolutions.jts.geom.GeometryFactory convertFactory(
                org.locationtech.jts.geom.GeometryFactory factory) {
            return new com.vividsolutions.jts.geom.GeometryFactory(
                    convertPrecisionModel(factory.getPrecisionModel()), factory.getSRID());
        }

        /**
         * Convert the supplied {@code PrecisionModel}.
         *
         * @param precisionModel the precision model
         *
         * @return the converted precision model
         */
        private com.vividsolutions.jts.geom.PrecisionModel convertPrecisionModel(
                org.locationtech.jts.geom.PrecisionModel precisionModel) {
            if (precisionModel.getType() == org.locationtech.jts.geom.PrecisionModel.FIXED) {
                return new com.vividsolutions.jts.geom.PrecisionModel(precisionModel.getScale());
            } else if (precisionModel.getType() == org.locationtech.jts.geom.PrecisionModel.FLOATING) {
                return new com.vividsolutions.jts.geom.PrecisionModel(
                        com.vividsolutions.jts.geom.PrecisionModel.FLOATING);
            } else if (precisionModel.getType() == org.locationtech.jts.geom.PrecisionModel.FLOATING_SINGLE) {
                return new com.vividsolutions.jts.geom.PrecisionModel(
                        com.vividsolutions.jts.geom.PrecisionModel.FLOATING_SINGLE);
            } else {
                return new com.vividsolutions.jts.geom.PrecisionModel(
                        new com.vividsolutions.jts.geom.PrecisionModel.Type(
                                precisionModel.getType().toString()));
            }
        }

        /**
         * Convert the supplied {@code Coordinate}.
         *
         * @param coordinate the coordinate
         *
         * @return the converted coordinate
         */
        private com.vividsolutions.jts.geom.Coordinate convertCoordinate(
                org.locationtech.jts.geom.Coordinate coordinate) {
            return new com.vividsolutions.jts.geom.Coordinate(coordinate.x, coordinate.y, coordinate.z);
        }

        /**
         * Convert the supplied {@code Coordinate} array.
         *
         * @param coordinates the coordinates
         *
         * @return the converted coordinates
         */
        private com.vividsolutions.jts.geom.Coordinate[] convertCoordinates(
                org.locationtech.jts.geom.Coordinate[] coordinates) {
            int n = coordinates.length;
            com.vividsolutions.jts.geom.Coordinate[] array
                    = new com.vividsolutions.jts.geom.Coordinate[n];
            for (int i = 0; i < n; ++i) {
                array[i] = convertCoordinate(coordinates[i]);
            }
            return array;
        }

        /**
         * Convert the supplied {@code LineString} to a {@code LinearRing}.
         *
         * @param factory  the geometry factory
         * @param geometry the line string
         *
         * @return the linear ring
         */
        private com.vividsolutions.jts.geom.LinearRing createLinearRing(
                com.vividsolutions.jts.geom.GeometryFactory factory,
                org.locationtech.jts.geom.LineString geometry) {
            return factory.createLinearRing(convertCoordinates(geometry.getCoordinates()));
        }
    }

    private static final class VS2LT {
        /**
         * Convert the supplied {@code Geometry}.
         *
         * @param geometry the geometry
         *
         * @return the converted geometry
         */
        org.locationtech.jts.geom.Geometry convert(com.vividsolutions.jts.geom.Geometry geometry) {
            if (geometry == null) {
                return null;
            }

            org.locationtech.jts.geom.GeometryFactory factory = convertFactory(geometry.getFactory());

            switch (geometry.getGeometryType()) {
                case POINT:
                    return factory.createPoint(convertCoordinate(geometry.getCoordinate()));
                case LINE_STRING:
                    return factory.createLineString(convertCoordinates(geometry.getCoordinates()));
                case POLYGON: {
                    com.vividsolutions.jts.geom.Polygon p = (com.vividsolutions.jts.geom.Polygon) geometry;
                    int n = p.getNumInteriorRing();
                    org.locationtech.jts.geom.LinearRing[] holes = new org.locationtech.jts.geom.LinearRing[n];
                    for (int i = 0; i < n; ++i) {
                        holes[i] = createLinearRing(factory, p.getInteriorRingN(i));
                    }
                    return factory.createPolygon(createLinearRing(factory, p.getExteriorRing()), holes);
                }
                case MULTI_POINT:
                case MULTI_LINE_STRING:
                case MULTI_POLYGON:
                case GEOMETRY_COLLECTION: {
                    int n = geometry.getNumGeometries();
                    List<org.locationtech.jts.geom.Geometry> array = new ArrayList<>(n);
                    for (int i = 0; i < n; ++i) {
                        array.add(convert(geometry.getGeometryN(i)));
                    }
                    return factory.buildGeometry(array);
                }
                default:
                    throw new IllegalArgumentException("Unsupported ol geometry: " + geometry);
            }

        }

        /**
         * Convert the supplied {@code GeometryFactory}.
         *
         * @param factory the factory
         *
         * @return the converted factory
         */
        private org.locationtech.jts.geom.GeometryFactory convertFactory(
                com.vividsolutions.jts.geom.GeometryFactory factory) {
            return new org.locationtech.jts.geom.GeometryFactory(
                    convertPrecisionModel(factory.getPrecisionModel()),
                    factory.getSRID());
        }

        /**
         * Convert the supplied {@code PrecisionModel}.
         *
         * @param precisionModel the precision model
         *
         * @return the converted precision model
         */
        private org.locationtech.jts.geom.PrecisionModel convertPrecisionModel(
                com.vividsolutions.jts.geom.PrecisionModel precisionModel) {
            if (precisionModel.getType() == com.vividsolutions.jts.geom.PrecisionModel.FIXED) {
                return new org.locationtech.jts.geom.PrecisionModel(precisionModel.getScale());
            } else if (precisionModel.getType() == com.vividsolutions.jts.geom.PrecisionModel.FLOATING) {
                return new org.locationtech.jts.geom.PrecisionModel(
                        org.locationtech.jts.geom.PrecisionModel.FLOATING);
            } else if (precisionModel.getType() == com.vividsolutions.jts.geom.PrecisionModel.FLOATING_SINGLE) {
                return new org.locationtech.jts.geom.PrecisionModel(
                        org.locationtech.jts.geom.PrecisionModel.FLOATING_SINGLE);
            } else {
                return new org.locationtech.jts.geom.PrecisionModel(
                        new org.locationtech.jts.geom.PrecisionModel.Type(
                                precisionModel.getType().toString()));
            }
        }

        /**
         * Convert the supplied {@code Coordinate}.
         *
         * @param coordinate the coordinate
         *
         * @return the converted coordinate
         */
        private org.locationtech.jts.geom.Coordinate convertCoordinate(
                com.vividsolutions.jts.geom.Coordinate coordinate) {
            return new org.locationtech.jts.geom.Coordinate(coordinate.x, coordinate.y, coordinate.z);
        }

        /**
         * Convert the supplied {@code Coordinate} array.
         *
         * @param coordinates the coordinates
         *
         * @return the converted coordinates
         */
        private org.locationtech.jts.geom.Coordinate[] convertCoordinates(
                com.vividsolutions.jts.geom.Coordinate[] coordinates) {
            int n = coordinates.length;
            org.locationtech.jts.geom.Coordinate[] array = new org.locationtech.jts.geom.Coordinate[n];
            for (int i = 0; i < n; ++i) {
                array[i] = convertCoordinate(coordinates[i]);
            }
            return array;
        }

        /**
         * Convert the supplied {@code LineString} to a {@code LinearRing}.
         *
         * @param factory  the geometry factory
         * @param geometry the line string
         *
         * @return the linear ring
         */
        private org.locationtech.jts.geom.LinearRing createLinearRing(
                org.locationtech.jts.geom.GeometryFactory factory,
                com.vividsolutions.jts.geom.LineString geometry) {
            return factory.createLinearRing(convertCoordinates(geometry.getCoordinates()));
        }
    }

}
