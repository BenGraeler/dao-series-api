/*
 * Copyright (C) 2015-2017 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public License
 * version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */

package org.n52.series.db.da;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.n52.io.crs.CRSUtils;
import org.n52.io.request.IoParameters;
import org.n52.io.response.CategoryOutput;
import org.n52.io.response.FeatureOutput;
import org.n52.io.response.OfferingOutput;
import org.n52.io.response.ParameterOutput;
import org.n52.io.response.PhenomenonOutput;
import org.n52.io.response.ProcedureOutput;
import org.n52.io.response.ServiceOutput;
import org.n52.io.response.dataset.SeriesParameters;
import org.n52.series.db.DataAccessException;
import org.n52.series.db.HibernateSessionStore;
import org.n52.series.db.beans.CategoryEntity;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.DescribableEntity;
import org.n52.series.db.beans.FeatureEntity;
import org.n52.series.db.beans.MeasurementDatasetEntity;
import org.n52.series.db.beans.OfferingEntity;
import org.n52.series.db.beans.PhenomenonEntity;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.ServiceEntity;
import org.n52.series.db.dao.DbQuery;
import org.n52.series.db.dao.DbQueryFactory;
import org.n52.web.ctrl.UrlHelper;
import org.n52.web.exception.BadRequestException;
import org.n52.web.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class SessionAwareRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionAwareRepository.class);

    protected UrlHelper urHelper = new UrlHelper();

    // via xml or db
    @Autowired(required = false)
    protected ServiceEntity serviceEntity;

    @Autowired
    protected DbQueryFactory dbQueryFactory;

    private final CRSUtils crsUtils = CRSUtils.createEpsgStrictAxisOrder();

    // if null, database is expected to have srs set properly
    private String databaseSrid;

    @Autowired
    private HibernateSessionStore sessionStore;

    protected DbQuery getDbQuery(IoParameters parameters) {
        return dbQueryFactory.createFrom(parameters);
    }

    public HibernateSessionStore getSessionStore() {
        return sessionStore;
    }

    public void setSessionStore(HibernateSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    protected CRSUtils getCrsUtils() {
        return crsUtils;
    }

    protected String getDatabaseSrid() {
        return databaseSrid;
    }

    protected Long parseId(String id) throws BadRequestException {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            LOGGER.debug("Unable to parse {} to Long.", e);
            throw new ResourceNotFoundException("Resource with id '" + id + "' could not be found.");
        }
    }

    public void returnSession(Session session) {
        sessionStore.returnSession(session);
    }

    public Session getSession() {
        try {
            return sessionStore.getSession();
        } catch (Throwable e) {
            throw new IllegalStateException("Could not get hibernate session.", e);
        }
    }

    public void setDatabaseSrid(String databaseSrid) {
        this.databaseSrid = databaseSrid;
    }

    protected Map<String, SeriesParameters> createTimeseriesList(List<MeasurementDatasetEntity> series,
                                                                 DbQuery parameters)
            throws DataAccessException {
        Map<String, SeriesParameters> timeseriesOutputs = new HashMap<>();
        for (MeasurementDatasetEntity timeseries : series) {
            if (!timeseries.getProcedure()
                            .isReference()) {
                String timeseriesId = Long.toString(timeseries.getPkid());
                timeseriesOutputs.put(timeseriesId, createTimeseriesOutput(timeseries, parameters));
            }
        }
        return timeseriesOutputs;
    }

    protected SeriesParameters createTimeseriesOutput(MeasurementDatasetEntity series, DbQuery parameters)
            throws DataAccessException {
        SeriesParameters metadata = new SeriesParameters();
        metadata.setService(getCondensedService(series.getService(), parameters));
        metadata.setOffering(getCondensedOffering(series.getOffering(), parameters));
        metadata.setProcedure(getCondensedProcedure(series.getProcedure(), parameters));
        metadata.setPhenomenon(getCondensedPhenomenon(series.getPhenomenon(), parameters));
        metadata.setFeature(getCondensedFeature(series.getFeature(), parameters));
        metadata.setCategory(getCondensedCategory(series.getCategory(), parameters));
        return metadata;
    }

    protected SeriesParameters createSeriesParameters(DatasetEntity< ? > series, DbQuery parameters, Session session)
            throws DataAccessException {
        SeriesParameters metadata = new SeriesParameters();
        metadata.setService(getCondensedExtendedService(series.getService(), parameters));
        metadata.setOffering(getCondensedExtendedOffering(series.getOffering(), parameters));
        metadata.setProcedure(getCondensedExtendedProcedure(series.getProcedure(), parameters));
        metadata.setPhenomenon(getCondensedExtendedPhenomenon(series.getPhenomenon(), parameters));
        metadata.setFeature(getCondensedExtendedFeature(series.getFeature(), parameters));
        metadata.setCategory(getCondensedExtendedCategory(series.getCategory(), parameters));
        // seriesParameter.setPlatform(getCondensedPlatform(series, parameters, session)); // #309
        return metadata;
    }

    protected PhenomenonOutput getCondensedPhenomenon(PhenomenonEntity entity, DbQuery parameters) {
        return createCondensed(new PhenomenonOutput(), entity, parameters);
    }

    protected PhenomenonOutput getCondensedExtendedPhenomenon(PhenomenonEntity entity, DbQuery parameters) {
        return createCondensed(new PhenomenonOutput(),
                               entity,
                               parameters,
                               urHelper.getPhenomenaHrefBaseUrl(parameters.getHrefBase()));
    }

    protected OfferingOutput getCondensedOffering(OfferingEntity entity, DbQuery parameters) {
        return createCondensed(new OfferingOutput(), entity, parameters);
    }

    protected ServiceOutput getCondensedService(ServiceEntity entity, DbQuery parameters) {
        ServiceEntity service = getServiceEntity(entity);
        return createCondensed(new ServiceOutput(), service, parameters);
    }

    protected OfferingOutput getCondensedExtendedOffering(OfferingEntity entity, DbQuery parameters) {
        return createCondensed(new OfferingOutput(),
                               entity,
                               parameters,
                               urHelper.getOfferingsHrefBaseUrl(parameters.getHrefBase()));
    }

    public void setServiceEntity(ServiceEntity serviceEntity) {
        this.serviceEntity = serviceEntity;
    }

    protected ServiceEntity getServiceEntity() {
        return serviceEntity;
    }

    protected ServiceEntity getServiceEntity(DescribableEntity entity) {
        assertServiceAvailable(entity);
        return entity.getService() != null
                ? entity.getService()
                : serviceEntity;
    }

    protected ServiceOutput getCondensedExtendedService(ServiceEntity entity, DbQuery parameters) {
        ServiceEntity service = getServiceEntity(entity);
        final String hrefBase = urHelper.getServicesHrefBaseUrl(parameters.getHrefBase());
        return createCondensed(new ServiceOutput(), service, parameters, hrefBase);
    }

    protected <T extends ParameterOutput> T createCondensed(T outputvalue,
                                                            DescribableEntity entity,
                                                            DbQuery parameters) {
        outputvalue.setLabel(entity.getLabelFrom(parameters.getLocale()));
        outputvalue.setId(Long.toString(entity.getPkid()));
        return outputvalue;
    }

    private <T extends ParameterOutput> T createCondensed(T outputvalue,
                                                          DescribableEntity entity,
                                                          DbQuery parameters,
                                                          String hrefBase) {
        createCondensed(outputvalue, entity, parameters);
        outputvalue.setHref(hrefBase + "/" + outputvalue.getId());
        return outputvalue;
    }

    protected ProcedureOutput getCondensedProcedure(ProcedureEntity entity, DbQuery parameters) {
        return createCondensed(new ProcedureOutput(), entity, parameters);
    }

    protected ProcedureOutput getCondensedExtendedProcedure(ProcedureEntity entity, DbQuery parameters) {
        return createCondensed(new ProcedureOutput(),
                               entity,
                               parameters,
                               urHelper.getProceduresHrefBaseUrl(parameters.getHrefBase()));
    }

    protected FeatureOutput getCondensedFeature(FeatureEntity entity, DbQuery parameters) {
        return createCondensed(new FeatureOutput(), entity, parameters);
    }

    protected FeatureOutput getCondensedExtendedFeature(FeatureEntity entity, DbQuery parameters) {
        return createCondensed(new FeatureOutput(),
                               entity,
                               parameters,
                               urHelper.getFeaturesHrefBaseUrl(parameters.getHrefBase()));
    }

    protected CategoryOutput getCondensedCategory(CategoryEntity entity, DbQuery parameters) {
        return createCondensed(new CategoryOutput(), entity, parameters);
    }

    protected CategoryOutput getCondensedExtendedCategory(CategoryEntity entity, DbQuery parameters) {
        return createCondensed(new CategoryOutput(),
                               entity,
                               parameters,
                               urHelper.getCategoriesHrefBaseUrl(parameters.getHrefBase()));
    }

    private void assertServiceAvailable(DescribableEntity entity) throws IllegalStateException {
        if (serviceEntity == null && entity == null) {
            throw new IllegalStateException("No service instance available");
        }
    }

}
