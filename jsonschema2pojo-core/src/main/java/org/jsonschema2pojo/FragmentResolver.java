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

import static java.util.Arrays.*;
import static org.apache.commons.lang3.StringUtils.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;

public class FragmentResolver {

    protected final String encoding;

    public FragmentResolver(String encoding) {
        this.encoding = encoding;
    }

    public JsonNode resolve(JsonNode tree, String path) {

        return resolve(tree, new ArrayList<String>(asList(split(path, "#/."))));

    }

    private JsonNode resolve(JsonNode tree, List<String> path) {

        if (path.isEmpty()) {
            return tree;
        } else {
            String part = path.remove(0);
            try {
                part = URLDecoder.decode(part, encoding);
            } catch (UnsupportedEncodingException e) {
                Log.e("FragmentResolver", "Could not decode path part from encoding " + encoding);
                e.printStackTrace();
            }

            if (tree.isArray()) {
                try {
                    int index = Integer.parseInt(part);
                    return resolve(tree.get(index), path);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a valid array index: " + part);
                }
            }

            if (tree.has(part)) {
                return resolve(tree.get(part), path);
            } else {
                throw new IllegalArgumentException("Path not present: " + part);
            }
        }

    }
}
