/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.lavender.modules;

import net.oneandone.lavender.index.Label;
import net.oneandone.lavender.publisher.Distributor;
import net.oneandone.lavender.publisher.Resource;
import net.oneandone.lavender.config.Filter;

import java.io.IOException;

/** Contains resources. */
public abstract class Module implements Iterable<Resource> {
    public static final String DEFAULT_STORAGE = "lavender";

    //--

    private final Filter filter;
    private final String storage;
    private final boolean lavendelize;
    private final String pathPrefix;

    public Module(Filter filter, String storage, boolean lavendelize, String pathPrefix) {
        if (filter == null) {
            throw new IllegalArgumentException();
        }
        this.filter = filter;
        this.storage = storage;
        this.lavendelize = lavendelize;
        this.pathPrefix = pathPrefix;
    }

    public String getStorage() {
        return storage;
    }

    public Filter getFilter() {
        return filter;
    }

    /** @return number of changed (updated or added) files */
    public long run(Distributor distributor) throws IOException {
        Filter filter;
        Label label;
        long count;
        byte[] data;
        byte[] md5;

        count = 0;
        filter = getFilter();
        for (Resource resource : this) {
            if (filter.isIncluded(resource.getPath())) {
                data = resource.readData();
                md5 = Resource.md5(data);
                if (lavendelize) {
                    label = resource.labelLavendelized(pathPrefix, md5);
                } else {
                    label = resource.labelNormal(pathPrefix, md5);
                }
                if (distributor.write(label, data)) {
                    count++;
                }
            }
        }
        return count;
    }
}