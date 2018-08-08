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

import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class DefaultResource extends Resource {
    public static DefaultResource forBytes(World world, String path, byte... bytes) throws IOException {
        FileNode temp;

        temp = world.getTemp().createTempFile();
        temp.writeBytes(bytes);
        return forNode(temp, path);
    }

    public static DefaultResource forNode(Node node, String path) throws IOException {
        return new DefaultResource(node, path, node.getLastModified());
    }

    private final Node node;
    private final String origin;
    private final String path;
    private final long lastModified;


    private byte[] lazyBytes;
    private byte[] lazyMd5;

    private DefaultResource(Node node, String path, long lastModified) {
        this.node = node;
        this.origin = node.getUri().toString();
        this.path = path;
        this.lastModified = lastModified;

        this.lazyBytes = null;
        this.lazyMd5 = null;
    }

    public String getPath() {
        return path;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isOutdated() {
        try {
            return node.getLastModified() != lastModified;
        } catch (GetLastModifiedException e) {
            // not found
            return true;
        }
    }

    public String getOrigin() {
        return origin;
    }

    public byte[] getMd5() throws IOException {
        if (lazyMd5 == null) {
            lazyMd5 = md5(getData());
        }
        return lazyMd5;
    }

    public byte[] getData() throws IOException {
        if (lazyBytes == null) {
            lazyBytes = node.readBytes();
        }
        return lazyBytes;
    }
}
