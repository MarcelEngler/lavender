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
package net.oneandone.lavender.publisher;

import net.oneandone.lavender.index.Label;
import net.oneandone.lavender.publisher.config.Filter;
import net.oneandone.lavender.publisher.pustefix.PustefixExtractor;
import net.oneandone.lavender.publisher.svn.SvnExtractorConfig;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts resources */
public abstract class Extractor implements Iterable<Resource> {
    public static final String DEFAULT_STORAGE = "lavender";

    private static final String PROPERTIES = "WEB-INF/lavendel.properties";

    public static List<Extractor> fromWar(Log log, FileNode war, String svnUsername, String svnPassword) throws IOException {
        List<Extractor> result;
        Properties properties;

        log.verbose("scanning " + war);
        result = new ArrayList<>();
        properties = getConfig(war.toPath().toFile());
        result.add(PustefixExtractor.forProperties(war.toPath().toFile(), properties));
        for (SvnExtractorConfig config : SvnExtractorConfig.parse(properties)) {
            log.info("adding svn extractor " + config.name);
            result.add(config.create(war.getWorld(), log, svnUsername, svnPassword));
        }
        return result;
    }

    private static Properties getConfig(File war) throws IOException {
        Properties result;
        ZipFile zip;
        ZipEntry entry;
        InputStream src;

        result = new Properties();
        zip = new ZipFile(war);
        entry = zip.getEntry(PROPERTIES);
        if (entry == null) {
            throw new FileNotFoundException("missing " + PROPERTIES);
        }
        src = zip.getInputStream(entry);
        result.load(src);
        src.close();
        return result;
    }

    //--

    private final Filter filter;
    private final String storage;
    private final boolean lavendelize;
    private final String pathPrefix;

    public Extractor(Filter filter, String storage, boolean lavendelize, String pathPrefix) {
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
        Filter config;
        Label label;
        boolean changed;
        long count;

        count = 0;
        config = getFilter();
        for (Resource resource : this) {
            if (config.isIncluded(resource.getPath())) {
                if (lavendelize) {
                    label = resource.labelLavendelized(pathPrefix);
                } else {
                    label = resource.labelNormal(pathPrefix);
                }
                if (distributor.write(label, resource.getData())) {
                    count++;
                }
            }
        }
        return count;
    }
}