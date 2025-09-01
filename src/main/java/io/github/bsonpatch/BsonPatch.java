/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.bsonpatch;

import org.bson.BsonArray;
import org.bson.BsonNull;
import org.bson.BsonValue;

import java.util.EnumSet;
import java.util.Iterator;

import static io.github.bsonpatch.InPlaceApplyProcessor.cloneBsonValue;

public final class BsonPatch {

    private BsonPatch() {}

    private static BsonValue getPatchStringAttr(BsonValue bsonNode, String attr) {
        BsonValue child = getPatchAttr(bsonNode, attr);

        if (!child.isString())
            throw new InvalidBsonPatchException("Invalid JSON Patch payload (non-text '" + attr + "' field)");

        return child;
    }

    private static BsonValue getPatchAttr(BsonValue bsonNode, String attr) {
    	BsonValue child = bsonNode.asDocument().get(attr);
        if (child == null)
            throw new InvalidBsonPatchException("Invalid BSON Patch payload (missing '" + attr + "' field)");

        return child;
    }

    private static BsonValue getPatchAttrWithDefault(BsonValue bsonNode, String attr, BsonValue defaultValue) {
    	BsonValue child = bsonNode.asDocument().get(attr);
        if (child == null)
            return defaultValue;
        else
            return child;
    }

    private static void process(BsonArray patch, BsonPatchProcessor processor, EnumSet<CompatibilityFlags> flags)
            throws InvalidBsonPatchException {

        Iterator<BsonValue> operations = patch.iterator();
        while (operations.hasNext()) {
        	BsonValue bsonNode = operations.next();
            if (!bsonNode.isDocument()) throw new InvalidBsonPatchException("Invalid BSON Patch payload (not an object)");
            Operation operation = Operation.fromRfcName(getPatchStringAttr(bsonNode, Constants.OP).asString().getValue().replaceAll("\"", ""));
            JsonPointer path = JsonPointer.parse(getPatchStringAttr(bsonNode, Constants.PATH).asString().getValue());

            try {
	            switch (operation) {
		            case REMOVE: {
		                processor.remove(path);
		                break;
		            }
		
		            case ADD: {
		            	BsonValue value;
		                if (!flags.contains(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
		                    value = getPatchAttr(bsonNode, Constants.VALUE);
		                else
		                    value = getPatchAttrWithDefault(bsonNode, Constants.VALUE, BsonNull.VALUE);
		                processor.add(path, cloneBsonValue(value));
		                break;
		            }
		
		            case REPLACE: {
		            	BsonValue value;
		                if (!flags.contains(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
		                    value = getPatchAttr(bsonNode, Constants.VALUE);
		                else
		                    value = getPatchAttrWithDefault(bsonNode, Constants.VALUE, BsonNull.VALUE);
		                processor.replace(path, cloneBsonValue(value));
		                break;
		            }
		
		            case MOVE: {
		                JsonPointer fromPath = JsonPointer.parse(getPatchStringAttr(bsonNode, Constants.FROM).asString().getValue());
		                processor.move(fromPath, path);
		                break;
		            }
		
		            case COPY: {
		                JsonPointer fromPath = JsonPointer.parse(getPatchStringAttr(bsonNode, Constants.FROM).asString().getValue());
		                processor.copy(fromPath, path);
		                break;
		            }
		
		            case TEST: {
		            	BsonValue value;
		                if (!flags.contains(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
		                    value = getPatchAttr(bsonNode, Constants.VALUE);
		                else
		                    value = getPatchAttrWithDefault(bsonNode, Constants.VALUE, BsonNull.VALUE);
		                processor.test(path, cloneBsonValue(value));
		                break;
		            }
	            }
            }
            catch (JsonPointerEvaluationException e) {
                throw new BsonPatchApplicationException(e.getMessage(), operation, e.getPath());
            }
         }
    }

    public static void validate(BsonArray patch, EnumSet<CompatibilityFlags> flags) throws InvalidBsonPatchException {
        process(patch, NoopProcessor.INSTANCE, flags);
    }

    public static void validate(BsonArray patch) throws InvalidBsonPatchException {
        validate(patch, CompatibilityFlags.defaults());
    }

    public static BsonValue apply(BsonArray patch, BsonValue source, EnumSet<CompatibilityFlags> flags) throws BsonPatchApplicationException {
        CopyingApplyProcessor processor = new CopyingApplyProcessor(source, flags);
        process(patch, processor, flags);
        return processor.result();
    }

    public static BsonValue apply(BsonArray patch, BsonValue source) throws BsonPatchApplicationException {
        return apply(patch, source, CompatibilityFlags.defaults());
    }

    public static void applyInPlace(BsonArray patch, BsonValue source) {
        applyInPlace(patch, source, CompatibilityFlags.defaults());
    }

    public static void applyInPlace(BsonArray patch, BsonValue source, EnumSet<CompatibilityFlags> flags) {
        InPlaceApplyProcessor processor = new InPlaceApplyProcessor(source, flags);
        process(patch, processor, flags);
    }

}
