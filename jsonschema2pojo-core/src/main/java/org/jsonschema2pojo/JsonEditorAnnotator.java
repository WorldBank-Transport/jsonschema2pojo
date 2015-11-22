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

package org.jsonschema2pojo;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.*;
import org.jsonschema2pojo.annotations.*;


public class JsonEditorAnnotator extends AbstractAnnotator {

    // as used by json-editor
    static int DEFAULT_PROPERTY_ORDER = 1000;

    static Logger Log = Logger.getLogger("JsonEditorAnnotator");

    @Override
    public void propertyOrder(JDefinedClass clazz, JsonNode propertiesNode) {
        JAnnotationArrayMember annotationValue = clazz.annotate(JsonPropertyOrder.class).paramArray("value");

        SortedMap<Integer, String> fieldOrders = new TreeMap();

        for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
            String propertyName = properties.next();

            if (propertyName.equals("_localId")) {
                continue;
            }

            JsonNode element = propertiesNode.findValue(propertyName);

            int order = DEFAULT_PROPERTY_ORDER;
            if (element != null && element.has("propertyOrder")) {
                JsonNode propertyOrder = element.findValue("propertyOrder");
                order = propertyOrder.asInt(DEFAULT_PROPERTY_ORDER);
            }

            fieldOrders.put(order, propertyName);
        }

        for (String name: fieldOrders.values()) {
            annotationValue.param(name);
        }
    }

    @Override
    public void propertyField(JFieldVar field, JDefinedClass clazz,
                              String propertyName, JsonNode propertyNode) {

        if (propertyNode.has("title")) {
            String title = propertyNode.get("title").asText();

            if (!title.isEmpty()) {
                JAnnotationUse titleAnnotation = field.annotate(Title.class);
                titleAnnotation.param("value", title);
            }
        }

        if (propertyNode.has("plural_title")) {
            String pluralTitle = propertyNode.get("plural_title").asText();

            if (!pluralTitle.isEmpty()) {
                JAnnotationUse pluralTitleAnnotation = field.annotate(PluralTitle.class);
                pluralTitleAnnotation.param("value", pluralTitle);
            }
        }

        if (propertyNode.has("multiple")) {
            Boolean multiple = propertyNode.get("multiple").asBoolean();
            JAnnotationUse multipleAnnotation = field.annotate(Multiple.class);
            multipleAnnotation.param("value", multiple);
        }

        if (propertyNode.has("fieldType")) {
            String fieldType = propertyNode.get("fieldType").asText();
            FieldTypes foundFieldType = FieldTypes.valueOf(fieldType);

            if (foundFieldType != null) {
                JAnnotationUse fieldTypeAnnotation = field.annotate(FieldType.class);
                fieldTypeAnnotation.param("value", foundFieldType);
            } else {
                Log.info("No field type found for " + fieldType);
            }
        }

        if (propertyNode.has("options")) {
            JsonNode options = propertyNode.get("options");
            if (options.has("hidden")) {
                Boolean isHidden = options.get("hidden").asBoolean();
                JAnnotationUse hiddenAnnotation = field.annotate(IsHidden.class);
                hiddenAnnotation.param("value", isHidden);
            }
        }

        if (propertyNode.has("description")) {
            String description = propertyNode.get("description").asText();

            if (!description.isEmpty()) {
                JAnnotationUse descriptionAnnotation = field.annotate(Description.class);
                descriptionAnnotation.param("value", description);
            }
        }

    }

    @Override
    public boolean isAdditionalPropertiesSupported() {
        return true;
    }
}
