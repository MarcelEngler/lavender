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
package net.oneandone.lavender.filter.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link Processor} for CSS content. This implementation scans the content for <code>url(...)</code> pattern and
 * rewrites found URIs.
 */
public class CssProcessor extends AbstractProcessor {

    /** The logger. */
    static final Logger LOG = LoggerFactory.getLogger(CssProcessor.class);

    /** The state of this processor. */
    protected State state = State.OTHER;

    /**
     * Instantiates a new CSS processor.
     */
    public CssProcessor() {
        super(LOG);
    }

    /**
     * An enum to track the state of this processor.
     */
    enum State {
        /** The 'U' of URL */
        URL_U(true, 'u', 'U'),

        /** The 'R' or URL */
        URL_R(true, 'r', 'R'),

        /** The 'L' of URL */
        URL_L(true, 'l', 'L'),

        /** The left parenthesis after URL */
        URL_LPAR(true, '('),

        /** The default state. */
        OTHER(false, ')');

        private boolean strict;
        private char[] chars;

        State(boolean strict, char... chars) {
            this.strict = strict;
            this.chars = chars;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void process(char c) throws IOException {
        // url(
        switch (state) {
        case OTHER:
            match(c, State.URL_U);
            break;
        case URL_U:
            match(c, State.URL_R);
            break;
        case URL_R:
            match(c, State.URL_L);
            break;
        case URL_L:
            match(c, State.URL_LPAR);
            break;
        case URL_LPAR:
            processUrl(c);
            break;

        default:
            throw new IllegalStateException("Unexpected state: " + state);
        }
    }

    protected void processUrl(char c) throws IOException {
        if (c == ')' && uriBuffer.length() > 0 && uriBuffer.charAt(uriBuffer.length() - 1) != '\\') {
            rewriteUrl();
            state = State.OTHER;
            out.write(c);
        } else {
            uriBuffer.append(c);
        }
    }

    /**
     * Rewrites the URL stored in urlBuffer.
     */
    protected void rewriteUrl() throws IOException {
        int start = 0;
        int end = uriBuffer.length();

        // Trim whitespaces
        while (start < uriBuffer.length() && isWhiteSpace(uriBuffer.charAt(start))) {
            start++;
        }
        while (end > 0 && isWhiteSpace(uriBuffer.charAt(end - 1))) {
            end--;
        }

        // Remove quotes
        if ((uriBuffer.charAt(start) == '\'' && uriBuffer.charAt(end - 1) == '\'') ||
                (uriBuffer.charAt(start) == '"' && uriBuffer.charAt(end - 1) == '"')) {
            start++;
            end--;
        }
        if (start > end) {
            throw new IllegalStateException("Uri is empty " + state);
        }

        String uri = unescapeUri(uriBuffer.substring(start, end));

        out.write(uriBuffer.substring(0, start));
        out.write(rewriteEngine.rewrite(uri, baseURI, contextPath));
        out.write(uriBuffer.substring(end));
        uriBuffer.setLength(0);
    }

    private String unescapeUri(String uri) {
        StringBuilder buffer = new StringBuilder();
        int pos = 0;
        while (pos < uri.length()) {
            char c = uri.charAt(pos);
            if (c != '\\') {
                buffer.append(c);
            }
            pos++;
        }
        return buffer.toString();
    }

    private boolean isWhiteSpace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }

    protected void match(char c, State... states) throws IOException {
        State newState = null;
        outer: for (State s : states) {
            for (char k : s.chars) {
                if (c == k) {
                    state = s;
                    newState = s;
                    break outer;
                }
            }
        }

        // reset state
        if (newState == null && state.strict) {
            state = State.OTHER;
        }

        out.write(c);
    }

}
