/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.utils.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.utils.json.JSONParser;
import org.apache.felix.utils.resource.ResourceBuilder;
import org.osgi.framework.BundleException;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * Repository using a JSON representation of resource metadata.
 * The json should be a map: the key is the resource uri and the
 * value is a map of resource headers.
 * The content of the URL can be gzipped.
 */
public class JsonRepository extends BaseRepository {

    private final UrlLoader loader;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonRepository(String url, long expiration) {
        loader = new UrlLoader(url, expiration) {
            @Override
            protected boolean doRead(InputStream is) throws IOException {
                return JsonRepository.this.doRead(is);
            }
        };
    }

    @Override
    public List<Resource> getResources() {
        checkAndLoadCache();
        lock.readLock().lock();
        try {
            return super.getResources();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<Requirement, Collection<Capability>> findProviders(Collection<? extends Requirement> requirements) {
        checkAndLoadCache();
        lock.readLock().lock();
        try {
            return super.findProviders(requirements);
        } finally {
            lock.readLock().unlock();
        }
    }

    protected void checkAndLoadCache() {
        loader.checkAndLoadCache();
    }

    protected boolean doRead(InputStream is) throws IOException {
        Map<String, Map<String, String>> metadatas = verify(new JSONParser(is).getParsed());
        lock.writeLock().lock();
        try {
            resources.clear();
            capSets.clear();
            for (Map.Entry<String, Map<String, String>> metadata : metadatas.entrySet()) {
                buildResource(metadata.getKey(), metadata.getValue());
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected void buildResource(String uri, Map<String, String> headerMap) throws IOException {
        try {
            Resource resource = ResourceBuilder.build(uri, headerMap);
            addResource(resource);
        } catch (BundleException e) {
            throw new IOException("Unable to read resource: " + uri, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> verify(Object value) {
        Map<?, ?> obj = Map.class.cast(value);
        for (Map.Entry<?, ?> entry : obj.entrySet()) {
            String.class.cast(entry.getKey());
            Map<?, ?> child = Map.class.cast(entry.getValue());
            for (Map.Entry<?, ?> ce : child.entrySet()) {
                String.class.cast(ce.getKey());
                String.class.cast(ce.getValue());
            }
        }
        return (Map<String, Map<String, String>>) obj;
    }

}
