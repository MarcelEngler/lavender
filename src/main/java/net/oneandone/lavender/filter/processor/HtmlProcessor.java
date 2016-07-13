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
import java.util.ArrayList;
import java.util.List;

public class HtmlProcessor extends AbstractProcessor {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(HtmlProcessor.class);

    /** The main state of this processor. */
    protected State state = State.NULL;

    /** The current tag, but only if it has attributes. CAUTION: properly set only between &lt; ... &gt;; outside of angle brackets, it contains the last value of tag. */
    protected HtmlTag tag = LavendertHtmlTag.NULL;

    /** The current attribute within an tag. */
    protected HtmlAttribute attr = LavenderHtmlAttribute.NULL;

    protected int attrIndex = -1;

    /** The tag buffer. */
    protected StringBuilder tagBuffer = new StringBuilder(100);

    /** The relevant attributes in the current tag, in order. */
    protected List<Value> attrs = new ArrayList<>();

    private HtmlTag[] knownTags;
    private HtmlAttribute[] knownAttributes;
    private UrlRewriteMatcher[] urlRewriteMatchers;

    /**
     * An enum to track the state of this processor.
     */
    enum State {
        NULL,

        //
        SPECIAL_START, SPECIAL_START_COMMENT_OR_CONDITION, SPECIAL_DOCTYPE, SPECIAL_CDATA, SPECIAL_COMMENT,

        //
        TAG_START, TAG, ATTRIBUTE_START, ATTRIBUTE, ATTRIBUTE_EQUALS, VALUE_START_SQ, VALUE_START_DQ, VALUE_START_UQ, VALUE
    }

    private static final class Value {
        private HtmlAttribute attr;
        private int start;
        private int end;

        private Value(HtmlAttribute attr, int start) {
            this.attr = attr;
            this.start = start;
        }
    }


    /**
     * Instantiates a new HTML processor.
     */
    public HtmlProcessor() {
        this(LavendertHtmlTag.values(), LavenderHtmlAttribute.values(), LavenderUrlRewriteMatcher.values());
    }

    /**
     * Instantiates a new HTML processor.
     */
    public HtmlProcessor(HtmlTag[] knownTags, HtmlAttribute[] knownAttributes, UrlRewriteMatcher[] urlRewriteMatchers) {
        super(LOG);
        this.knownTags = knownTags;
        this.knownAttributes = knownAttributes;
        this.urlRewriteMatchers = urlRewriteMatchers;
    }

    @Override
    public void flush() throws IOException {
        if (tagBuffer.length() > 0) {
            out.write(tagBuffer.toString());
        }
        super.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void process(char c) throws IOException {

        switch (state) {
            case NULL:
                matchTagStart(c);
                break;
            case SPECIAL_START:
                matchSpecialStart(c);
                break;
            case SPECIAL_START_COMMENT_OR_CONDITION:
                matchSpecialStartCommentOrCondition(c);
                break;
            case SPECIAL_DOCTYPE:
            case SPECIAL_COMMENT:
            case SPECIAL_CDATA:
                matchSpecialEnd(c);
                break;
            case TAG_START:
                matchTag(c);
                break;
            case TAG:
                matchInTag(c);
                break;
            case ATTRIBUTE_START:
                matchAttribute(c);
                break;
            case ATTRIBUTE:
                matchInAttribute(c);
                break;
            case ATTRIBUTE_EQUALS:
                matchValueStart(c);
                break;
            case VALUE_START_DQ:
                matchDoubleQuotedValue(c);
                break;
            case VALUE_START_SQ:
                matchSingleQuotedValue(c);
                break;
            case VALUE_START_UQ:
                matchUnquotedValue(c);
                break;

            default:
                throw new IllegalStateException("Unexpected state: " + state);
        }
    }

    protected void matchSpecialStart(char c) throws IOException {
        String t = tagBuffer.append(c).toString();
        if (t.equalsIgnoreCase("--")) {
            state = State.SPECIAL_START_COMMENT_OR_CONDITION;
        } else if (t.equalsIgnoreCase("[if") || t.equalsIgnoreCase("[endif")) {
            state = State.NULL;
            tagBuffer.setLength(0);
        } else if (t.equalsIgnoreCase("DOCTYPE")) {
            state = State.SPECIAL_DOCTYPE;
            tagBuffer.setLength(0);
        } else if (t.equalsIgnoreCase("[CDATA[")) {
            state = State.SPECIAL_CDATA;
            tagBuffer.setLength(0);
        }

        out.write(c);
    }

    protected void matchSpecialStartCommentOrCondition(char c) throws IOException {
        if (c == '[') {
            // condition
            state = State.NULL;
        } else if (c == '>') {
            // end of comment
            state = State.NULL;
        } else {
            state = State.SPECIAL_COMMENT;
        }

        tagBuffer.setLength(0);

        out.write(c);
    }

    private void matchSpecialEnd(char c) throws IOException {
        switch (state) {

            case SPECIAL_DOCTYPE:
                if (c == '>') {
                    state = State.NULL;
                }
                break;

            case SPECIAL_COMMENT:
                if (c == '-') {
                    tagBuffer.append(c);
                } else if (c == '>') {
                    if (tagBuffer.toString().endsWith("--")) {
                        state = State.NULL;
                    }
                    tagBuffer.setLength(0);
                } else {
                    tagBuffer.setLength(0);
                }
                break;

            case SPECIAL_CDATA:
                if (c == ']') {
                    tagBuffer.append(c);
                } else if (c == '>') {
                    if (tagBuffer.toString().endsWith("]]")) {
                        state = State.NULL;
                    }
                    tagBuffer.setLength(0);
                } else {
                    tagBuffer.setLength(0);
                }
                break;

            default:
                throw new IllegalStateException("" + state);
        }

        out.write(c);
    }

    protected void matchTagStart(char c) throws IOException {
        if (c == '<') {
            state = State.TAG_START;
        }

        out.write(c);
    }

    protected void matchTag(char c) throws IOException {
        if (Character.isSpaceChar(c)) {
            state = State.TAG;
            tag = findTagByName(tagBuffer.toString().toLowerCase());
            tagBuffer.append(c);
        } else if (c == '>') {
            processTagBuffer();
            state = State.NULL;
            tagBuffer.setLength(0);
            out.write(c);
        } else if (c == '!') {
            // comment
            state = State.SPECIAL_START;
            out.write(c);
        } else {
            // still in tag
            tagBuffer.append(c);
        }
    }

    protected void matchInTag(char c) throws IOException {
        if (c == '>') {
            processTagBuffer();
            state = State.NULL;
            tagBuffer.setLength(0);
            out.write(c);
        } else if (c == '/') {
            // ignore this
            tagBuffer.append(c);
        } else if (!Character.isSpaceChar(c)) {
            state = State.ATTRIBUTE_START;
            attrIndex = tagBuffer.length();
            tagBuffer.append(c);
        } else {
            tagBuffer.append(c);
        }
    }

    protected void matchAttribute(char c) throws IOException {
        if (c == '=' || Character.isSpaceChar(c)) {
            state = State.ATTRIBUTE;

            // match the attribute
            String a = tagBuffer.substring(attrIndex);
            attr = findMatchingAttribute(a);

            attrIndex = -1;

            if (c == '=') {
                matchInAttribute(c);
            } else {
                tagBuffer.append(c);
            }
        } else {
            // still in attribute
            tagBuffer.append(c);
        }
    }

    protected void matchInAttribute(char c) throws IOException {
        if (c == '=') {
            state = State.ATTRIBUTE_EQUALS;
            tagBuffer.append(c);
        } else if (!Character.isSpaceChar(c)) {
            state = State.ATTRIBUTE_START;
            attrIndex = tagBuffer.length();
            matchAttribute(c);
        } else {
            // space
            tagBuffer.append(c);
        }
    }

    protected void matchValueStart(char c) throws IOException {
        if (c == '"') {
            state = State.VALUE_START_DQ;
            tagBuffer.append(c);
            markValueStart();
        } else if (c == '\'') {
            state = State.VALUE_START_SQ;
            tagBuffer.append(c);
            markValueStart();
        } else if (!Character.isSpaceChar(c)) {
            state = State.VALUE_START_UQ;
            markValueStart();
            matchUnquotedValue(c);
        } else {
            // space
            tagBuffer.append(c);
        }
    }

    protected void matchUnquotedValue(char c) throws IOException {
        if (Character.isSpaceChar(c)) {
            state = State.VALUE;
            markValueLength();
            tagBuffer.append(c);
            state = State.TAG;
        } else {
            tagBuffer.append(c);
        }
    }

    protected void matchSingleQuotedValue(char c) throws IOException {
        if (c == '\'') {
            state = State.VALUE;
            markValueLength();
            tagBuffer.append(c);
            state = State.TAG;
        } else {
            tagBuffer.append(c);
        }
    }

    protected void matchDoubleQuotedValue(char c) throws IOException {
        if (c == '"') {
            state = State.VALUE;
            markValueLength();
            tagBuffer.append(c);
            state = State.TAG;
        } else {
            tagBuffer.append(c);
        }
    }

    protected void processTagBuffer() throws IOException {
        int index = 0;
        for (Value value : attrs) {
            out.write(tagBuffer.substring(index, value.start));
            boolean rewritten = false;

            if (value.attr == LavenderHtmlAttribute.STYLE) {
                rewriteCss(value);
                rewritten = true;
            } else if (matchesUrlRewriteMatcher(tag, value.attr)) {
                rewriteUrl(value);
                rewritten = true;
            }

            if (rewritten == false) {
                out.write(tagBuffer.substring(value.start, value.end));
            }

            index = value.end;
        }

        out.write(tagBuffer.substring(index));

        attrIndex = -1;
        attrs.clear();
        tagBuffer.setLength(0);
        uriBuffer.setLength(0);
    }

    protected void rewriteUrl(Value value) throws IOException {
        String str;

        str = rewriteEngine.rewrite(tagBuffer.substring(value.start, value.end), baseURI, contextPath);
        out.write(str);
    }

    protected void rewriteCss(Value value) throws IOException {
        CssProcessor cssProcessor = new CssProcessor();
        cssProcessor.setRewriteEngine(rewriteEngine, baseURI, contextPath);
        cssProcessor.setWriter(out);
        cssProcessor.process(tagBuffer, value.start, value.end - value.start);
    }

    protected void markValueStart() throws IOException {
        if (attr != LavenderHtmlAttribute.OTHER) {
            attrs.add(new Value(attr, tagBuffer.length()));
        }
    }

    protected void markValueLength() throws IOException {
        int size;
        Value value;

        size = attrs.size();
        if (size > 0) {
            value = attrs.get(size - 1);
            if (value.attr == attr) {
                value.end = tagBuffer.length();
            }
        }
    }

    /** @return first match or null */
    private Value lookupAttribute(HtmlAttribute attr) {
        for (Value value : attrs) {
            if (value.attr == attr) {
                return value;
            }
        }
        return null;
    }

    private HtmlTag findTagByName(String name) {
        for (HtmlTag tag : knownTags) {
            if (name.equals(tag.getName())) {
                return tag;
            }
        }

        return LavendertHtmlTag.OTHER;
    }

    private HtmlAttribute findMatchingAttribute(String name) {
        for (HtmlAttribute attribute : knownAttributes) {
            if (attribute.attributeMatches(name)) {
                return attribute;
            }
        }
        return LavenderHtmlAttribute.OTHER;
    }

    private boolean matchesUrlRewriteMatcher(HtmlTag htmlTag, HtmlAttribute rewriteAttribute) {
        for (UrlRewriteMatcher urlRewriteMatcher : urlRewriteMatchers) {
            HtmlAttribute matchingAttributeType = urlRewriteMatcher.getAttributeToMatch();
            String attributeValueToMatch = null;

            Value rel = lookupAttribute(matchingAttributeType);

            if (rel != null) {
                attributeValueToMatch = tagBuffer.substring(rel.start, rel.end);
            }

            if (urlRewriteMatcher.matches(htmlTag, rewriteAttribute, matchingAttributeType, attributeValueToMatch)) {
                return true;
            }
        }
        return false;
    }
}
