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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;
import org.n52.io.DatasetFactoryException;
import org.n52.io.request.FilterResolver;
import org.n52.io.request.IoParameters;
import org.n52.io.response.PlatformOutput;
import org.n52.io.response.dataset.Data;
import org.n52.io.response.dataset.DatasetOutput;
import org.n52.io.response.dataset.DatasetParameters;
import org.n52.io.response.dataset.ValueType;
import org.n52.series.db.DataAccessException;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.DescribableEntity;
import org.n52.series.db.dao.DatasetDao;
import org.n52.series.db.dao.DbQuery;
import org.n52.series.spi.search.DatasetSearchResult;
import org.n52.series.spi.search.SearchResult;
import org.n52.web.exception.BadQueryParameterException;
import org.n52.web.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TODO: JavaDoc
 *
 * @author <a href="mailto:h.bredel@52north.org">Henning Bredel</a>
 * @param <T>
 *        the dataset's type this repository is responsible for.
 */
public class DatasetRepository<T extends Data> extends SessionAwareRepository
        implements OutputAssembler<DatasetOutput> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetRepository.class);

    @Autowired
    private IDataRepositoryFactory dataRepositoryFactory;

    @Autowired
    private PlatformRepository platformRepository;

    @Override
    public boolean exists(String id, DbQuery parameters) throws DataAccessException {
        Session session = getSession();
        try {
            String dbId = ValueType.extractId(id);
            String handleAsFallback = parameters.getHandleAsValueTypeFallback();
            String valueType = ValueType.extractType(id, handleAsFallback);
            if (!dataRepositoryFactory.isKnown(valueType)) {
                return false;
            }
            DataRepository dataRepository = dataRepositoryFactory.create(valueType);
            DatasetDao< ? extends DatasetEntity> dao = getDatasetDao(valueType, session);
            Class datasetEntityType = dataRepository.getDatasetEntityType();
            return parameters.getParameters()
                             .isMatchDomainIds()
                                     ? dao.hasInstance(dbId, parameters, datasetEntityType)
                                     : dao.hasInstance(parseId(dbId), parameters, datasetEntityType);
        } catch (DatasetFactoryException ex) {
            throwNewCreateFactoryException(ex);
            return false;
        } finally {
            returnSession(session);
        }
    }

    @Override
    public List<DatasetOutput> getAllCondensed(DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            return getAllCondensed(query, session);
        } finally {
            returnSession(session);
        }
    }

    @Override
    public List<DatasetOutput> getAllCondensed(DbQuery query, Session session) throws DataAccessException {
        List<DatasetOutput> results = new ArrayList<>();
        FilterResolver filterResolver = query.getFilterResolver();
        if (query.getParameters()
                 .isMatchDomainIds()) {
            String valueType = query.getHandleAsValueTypeFallback();
            addCondensedResults(getDatasetDao(valueType, session), query, results, session);
            return results;
        }

        if (filterResolver.shallIncludeAllDatasetTypes()) {
            addCondensedResults(getDatasetDao(DatasetEntity.class, session), query, results, session);
        } else {
            for (String valueType : query.getValueTypes()) {
                addCondensedResults(getDatasetDao(valueType, session), query, results, session);
            }
        }
        return results;
    }

    private void addCondensedResults(DatasetDao< ? extends DatasetEntity> dao,
                                     DbQuery query,
                                     List<DatasetOutput> results,
                                     Session session)
            throws DataAccessException {
        for (DatasetEntity series : dao.getAllInstances(query)) {
            results.add(createCondensed(series, query, session));
        }
    }

    private DatasetDao< ? extends DatasetEntity> getDatasetDao(String valueType, Session session)
            throws DataAccessException {
        if (!("all".equalsIgnoreCase(valueType) || dataRepositoryFactory.isKnown(valueType))) {
            throw new BadQueryParameterException("invalid type: " + valueType);
        }
        return createDataAccessRepository(valueType, session);
    }

    private DatasetDao< ? extends DatasetEntity> getSeriesDao(String datasetId, DbQuery query, Session session)
            throws DataAccessException {
        String handleAsFallback = query.getHandleAsValueTypeFallback();
        final String valueType = ValueType.extractType(datasetId, handleAsFallback);
        if (!dataRepositoryFactory.isKnown(valueType)) {
            throw new ResourceNotFoundException("unknown type: " + valueType);
        }
        return createDataAccessRepository(valueType, session);
    }

    private DatasetDao< ? extends DatasetEntity> createDataAccessRepository(String valueType, Session session)
            throws DataAccessException {
        try {
            DataRepository dataRepository = dataRepositoryFactory.create(valueType);
            return getDatasetDao(dataRepository.getDatasetEntityType(), session);
        } catch (DatasetFactoryException e) {
            throw new DataAccessException(e.getMessage());
        }
    }

    private DatasetDao< ? extends DatasetEntity> getDatasetDao(Class< ? extends DatasetEntity> clazz, Session session) {
        return new DatasetDao<>(session, clazz);
    }

    @Override
    public List<DatasetOutput> getAllExpanded(DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            return getAllExpanded(query, session);
        } finally {
            returnSession(session);
        }
    }

    @Override
    public List<DatasetOutput> getAllExpanded(DbQuery query, Session session) throws DataAccessException {
        List<DatasetOutput> results = new ArrayList<>();
        FilterResolver filterResolver = query.getFilterResolver();
        if (query.getParameters()
                 .isMatchDomainIds()) {
            String valueType = query.getHandleAsValueTypeFallback();
            addExpandedResults(getDatasetDao(valueType, session), query, results, session);
            return results;
        }

        if (filterResolver.shallIncludeAllDatasetTypes()) {
            addExpandedResults(getDatasetDao(DatasetEntity.class, session), query, results, session);
        } else {
            for (String valueType : query.getValueTypes()) {
                addExpandedResults(getDatasetDao(valueType, session), query, results, session);
            }
        }
        return results;
    }

    private void addExpandedResults(DatasetDao< ? extends DatasetEntity> dao,
                                    DbQuery query,
                                    List<DatasetOutput> results,
                                    Session session)
            throws DataAccessException {
        for (DatasetEntity series : dao.getAllInstances(query)) {
            results.add(createExpanded(series, query, session));
        }
    }

    @Override
    public DatasetOutput getInstance(String id, DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            return getInstance(id, query, session);
        } finally {
            returnSession(session);
        }
    }

    @Override
    public DatasetOutput getInstance(String id, DbQuery query, Session session) throws DataAccessException {
        DatasetEntity instanceEntity = getInstanceEntity(id, query, session);
        return createExpanded(instanceEntity, query, session);
    }

    DatasetEntity getInstanceEntity(String id, DbQuery query, Session session) throws DataAccessException {
        String datasetId = ValueType.extractId(id);
        DatasetDao< ? extends DatasetEntity> dao = getSeriesDao(id, query, session);
        DatasetEntity instance = dao.getInstance(Long.parseLong(datasetId), query);
        instance.setPlatform(platformRepository.getPlatformEntity(instance, query, session));
        return instance;
    }

    @Override
    public Collection<SearchResult> searchFor(IoParameters paramters) {
        Session session = getSession();
        try {
            DatasetDao< ? extends DatasetEntity> dao = getDatasetDao(DatasetEntity.class, session);
            DbQuery query = getDbQuery(paramters);
            List< ? extends DatasetEntity> found = dao.find(query);
            return convertToSearchResults(found, query);
        } finally {
            returnSession(session);
        }
    }

    public List<SearchResult> convertToSearchResults(List< ? extends DescribableEntity> found, DbQuery query) {
        String locale = query.getLocale();
        String hrefBase = urlHelper.getDatasetsHrefBaseUrl(query.getHrefBase());
        List<SearchResult> results = new ArrayList<>();
        for (DescribableEntity searchResult : found) {
            String pkid = searchResult.getPkid()
                                      .toString();
            String label = searchResult.getLabelFrom(locale);
            results.add(new DatasetSearchResult(pkid, label, hrefBase));
        }
        return results;
    }

    // XXX refactor generics
    protected DatasetOutput createCondensed(DatasetEntity dataset, DbQuery query, Session session)
            throws DataAccessException {
        DatasetOutput output = new DatasetOutput(dataset.getValueType()) {};
        output.setLabel(dataset.getLabelFrom(query.getLocale()));
        output.setId(dataset.getPkid()
                           .toString());
        output.setDomainId(dataset.getDomainId());
        output.setHrefBase(urlHelper.getDatasetsHrefBaseUrl(query.getHrefBase()));
        PlatformOutput platform = getCondensedPlatform(dataset, query, session);
        output.setPlatformType(platform.getPlatformType());
        return output;
    }

    // XXX refactor generics
    protected DatasetOutput createExpanded(DatasetEntity dataset, DbQuery query, Session session)
            throws DataAccessException {
        try {
            DatasetOutput result = createCondensed(dataset, query, session);
            DatasetParameters parameters = createDatasetParameters(dataset, query, session);
            parameters.setPlatform(getCondensedPlatform(dataset, query, session));
            result.setDatasetParameters(parameters);

            if (dataset.getService() == null) {
                dataset.setService(getServiceEntity());
            }
            result.setUom(dataset.getUnitI18nName(query.getLocale()));
            DataRepository dataRepository = dataRepositoryFactory.create(dataset.getValueType());
            result.setFirstValue(dataRepository.getFirstValue(dataset, session, query));
            result.setLastValue(dataRepository.getLastValue(dataset, session, query));
            return result;
        } catch (DatasetFactoryException ex) {
            throwNewCreateFactoryException(ex);
            return null;
        }
    }

    private PlatformOutput getCondensedPlatform(DatasetEntity series, DbQuery query, Session session)
            throws DataAccessException {
        // platform has to be handled dynamically (see #309)
        return platformRepository.createCondensed(series, query, session);
    }

    public IDataRepositoryFactory getDataRepositoryFactory() {
        return dataRepositoryFactory;
    }

    public void setDataRepositoryFactory(IDataRepositoryFactory dataRepositoryFactory) {
        this.dataRepositoryFactory = dataRepositoryFactory;
    }

    private void throwNewCreateFactoryException(DatasetFactoryException e) throws DataAccessException {
        throw new DataAccessException("Could not create dataset factory.", e);
    }

}
