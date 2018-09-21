/*
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

import net.oneandone.sushi.util.Separator;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ModulePropertiesTest {
    private static Properties testPomInfo() throws IOException {
        Properties p;

        p = new Properties();
        p.put("ethernet", Separator.COMMA.join(ModuleProperties.ethernet()));
        p.put("basedir", "someDirectory");
        return p;
    }

    @Test
    public void empty() throws IOException {
        Properties p;

        p = new Properties();
        p.put("pustefix.relative", "base");
        assertEquals(0, ModuleProperties.parse(true, p, testPomInfo()).configs.size());
    }

    @Test
    public void one() throws IOException {
        Properties props;
        ModuleProperties result;
        ScmProperties config;

        props = new Properties();
        props.put("pustefix.relative", "relative");
        props.put("svn.foo", "svn");
        props.put("svn.foo.targetPathPrefix", "prefix");
        props.put("svn.foo.lavendelize", "false");
        props.put("svn.foo.relative", "sub");
        result = ModuleProperties.parse(false, props, testPomInfo());
        assertEquals("someDirectory/relative", result.source);
        assertEquals(1, result.configs.size());
        config = result.configs.iterator().next();
        assertEquals("foo", config.name);
        assertFalse(config.lavendelize);
        assertEquals("prefix", config.targetPathPrefix);
        assertEquals("svn", config.connectionProd);
        assertEquals("svn", config.connectionDevel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void undefinedNormal() throws IOException {
        Properties props;

        props = new Properties();
        ModuleProperties.parse(true, props, testPomInfo());
    }

    @Test(expected = IllegalArgumentException.class)
    public void undefinedSvn() throws IOException {
        Properties props;

        props = new Properties();
        props.put("pustefix.relative", "foo");
        props.put("svn.module.nosuchkey", "bla");
        ModuleProperties.parse(true, props, testPomInfo());
    }

    @Test
    public void more() throws IOException {
        Properties props;
        Collection<ScmProperties> result;

        props = new Properties();
        props.put("pustefix.relative", "foo");
        props.put("svn.foo", "1");
        props.put("svn.bar", "2");
        props.put("svn.baz", "3");
        result = ModuleProperties.parse(true, props, testPomInfo()).configs;
        assertEquals(3, result.size());
    }
}
