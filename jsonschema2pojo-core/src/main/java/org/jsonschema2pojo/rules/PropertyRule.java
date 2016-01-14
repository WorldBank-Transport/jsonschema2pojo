/**
 * Copyright © 2010-2014 Nokia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jsonschema2pojo.rules;

import static org.apache.commons.lang3.StringUtils.*;

import org.jsonschema2pojo.Schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocCommentable;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

/**
 * Applies the schema rules that represent a property definition.
 *
 * @see <a href=
 *      "http://tools.ietf.org/html/draft-zyp-json-schema-03#section-5.2">http:/
 *      /tools.ietf.org/html/draft-zyp-json-schema-03#section-5.2</a>
 */
public class PropertyRule implements Rule<JDefinedClass, JDefinedClass> {

    private final RuleFactory ruleFactory;

    protected PropertyRule(RuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    /**
     * Applies this schema rule to take the required code generation steps.
     * <p>
     * This rule adds a property to a given Java class according to the Java
     * Bean spec. A private field is added to the class, along with accompanying
     * accessor methods.
     * <p>
     * If this rule's schema mapper is configured to include builder methods
     * (see {@link org.jsonschema2pojo.GenerationConfig#isGenerateBuilders()} ),
     * then a builder method of the form <code>withFoo(Foo foo);</code> is also
     * added.
     *
     * @param nodeName
     *            the name of the property to be applied
     * @param node
     *            the node describing the characteristics of this property
     * @param jclass
     *            the Java class which should have this property added
     * @return the given jclass
     */
    @Override
    public JDefinedClass apply(String nodeName, JsonNode node, JDefinedClass jclass, Schema schema) {

        String propertyName = ruleFactory.getNameHelper().getPropertyName(nodeName);

        JType propertyType = ruleFactory.getSchemaRule().apply(nodeName, node, jclass, schema);

        node = resolveRefs(node, schema);

        int accessModifier = ruleFactory.getGenerationConfig().isIncludeAccessors() ? JMod.PRIVATE : JMod.PUBLIC;

        if (nodeName.equals("_localId")) {
            accessModifier |= JMod.FINAL; // make _localId fields final (assigned UUID)
        }

        JFieldVar field = jclass.field(accessModifier, propertyType, propertyName);
        propertyAnnotations(nodeName, node, schema, field);

        ruleFactory.getAnnotator().propertyField(field, jclass, nodeName, node);

        if (ruleFactory.getGenerationConfig().isIncludeAccessors()) {
            JMethod getter = addGetter(jclass, field, nodeName);
            propertyAnnotations(nodeName, node, schema, getter);

            JMethod setter = addSetter(jclass, field, nodeName);
            propertyAnnotations(nodeName, node, schema, setter);
        }

        if (ruleFactory.getGenerationConfig().isGenerateBuilders()) {
            addBuilder(jclass, field);
        }

        if (node.has("pattern")) {
            ruleFactory.getPatternRule().apply(nodeName, node.get("pattern"), field, schema);
        }

        ruleFactory.getDefaultRule().apply(nodeName, node.get("default"), field, schema);

        ruleFactory.getMinimumMaximumRule().apply(nodeName, node, field, schema);

        ruleFactory.getMinItemsMaxItemsRule().apply(nodeName, node, field, schema);

        ruleFactory.getMinLengthMaxLengthRule().apply(nodeName, node, field, schema);

        if (isObject(node) || isArray(node)) {
            ruleFactory.getValidRule().apply(nodeName, node, field, schema);
        }

        return jclass;
    }

    private void propertyAnnotations(String nodeName, JsonNode node, Schema schema, JDocCommentable generatedJavaConstruct) {
        if (node.has("title")) {
            ruleFactory.getTitleRule().apply(nodeName, node.get("title"), generatedJavaConstruct, schema);
        }

        if (node.has("description")) {
            ruleFactory.getDescriptionRule().apply(nodeName, node.get("description"), generatedJavaConstruct, schema);
        }

        if (node.has("required")) {
            ruleFactory.getRequiredRule().apply(nodeName, node.get("required"), generatedJavaConstruct, schema);
        }
    }

    private JsonNode resolveRefs(JsonNode node, Schema parent) {
        JsonNode ref = null;
        if (node.has("$ref")) {
            ref = node.get("$ref");
        } else if (node.has("items")) {
            JsonNode itemsNode = node.get("items");
            if (itemsNode.has("$ref")) {
                ref = itemsNode.get("$ref");
            }
        }

        if (ref != null) {
            Schema refSchema = ruleFactory.getSchemaStore().create(parent, ref.asText());
            JsonNode refNode = refSchema.getContent();
            return resolveRefs(refNode, parent);
        } else {
            return node;
        }
    }

    private boolean isObject(JsonNode node) {
        return node.path("type").asText().equals("object");
    }

    private boolean isArray(JsonNode node) {
        return node.path("type").asText().equals("array");
    }

    private JMethod addGetter(JDefinedClass c, JFieldVar field, String jsonPropertyName) {
        JMethod getter = c.method(JMod.PUBLIC, field.type(), getGetterName(jsonPropertyName, field.type()));

        // add @returns
        getter.javadoc().addReturn().append("The " + ruleFactory.getNameHelper().getPropertyName(jsonPropertyName));

        JBlock body = getter.body();
        body._return(field);

        ruleFactory.getAnnotator().propertyGetter(getter, jsonPropertyName);

        return getter;
    }

    private JMethod addSetter(JDefinedClass c, JFieldVar field, String jsonPropertyName) {
        JMethod setter = c.method(JMod.PUBLIC, void.class, getSetterName(jsonPropertyName));

        // add @param
        setter.javadoc().addParam(ruleFactory.getNameHelper().getPropertyName(jsonPropertyName)).append("The " + jsonPropertyName);

        JVar param = setter.param(field.type(), field.name());
        JBlock body = setter.body();
        body.assign(JExpr._this().ref(field), param);

        ruleFactory.getAnnotator().propertySetter(setter, jsonPropertyName);

        return setter;
    }

    private JMethod addBuilder(JDefinedClass c, JFieldVar field) {
        JMethod builder = c.method(JMod.PUBLIC, c, getBuilderName(field.name()));

        JVar param = builder.param(field.type(), field.name());
        JBlock body = builder.body();
        body.assign(JExpr._this().ref(field), param);
        body._return(JExpr._this());

        return builder;
    }

    private String getBuilderName(String propertyName) {
        propertyName = ruleFactory.getNameHelper().replaceIllegalCharacters(propertyName);
        return "with" + capitalize(ruleFactory.getNameHelper().capitalizeTrailingWords(propertyName));
    }

    private String getSetterName(String propertyName) {
        return ruleFactory.getNameHelper().getSetterName(propertyName);
    }

    private String getGetterName(String propertyName, JType type) {
        return ruleFactory.getNameHelper().getGetterName(propertyName, type);
    }

}
