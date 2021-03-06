/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * $Id$
 */
package org.forgerock.openidm.provisioner.openicf.commons;

import org.forgerock.json.crypto.JsonCryptoException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.JsonResourceException;
import org.forgerock.json.schema.validator.Constants;
import org.forgerock.json.schema.validator.exceptions.SchemaException;
import org.forgerock.openidm.core.ServerConstants;
import org.forgerock.openidm.crypto.CryptoService;
import org.forgerock.openidm.provisioner.Id;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author $author$
 * @version $Revision$ $Date$
 */
public class ObjectClassInfoHelper {
    private final static Logger logger = LoggerFactory.getLogger(ObjectClassInfoHelper.class);
    private final Set<AttributeInfoHelper> attributes;
    private final ObjectClass objectClass;
    private String nameAttribute = null;
    private final Set<String> attributesReturnedByDefault;

    /**
     * Create a custom object class.
     *
     * @param schema string representation for the name of the object class.
     * @throws IllegalArgumentException when objectClass is null
     * @throws org.forgerock.json.schema.validator.exceptions.SchemaException
     *
     */
    public ObjectClassInfoHelper(Map<String, Object> schema) throws SchemaException {
        objectClass = new ObjectClass((String) schema.get(ConnectorUtil.OPENICF_OBJECT_CLASS));
        Map<String, Object> properties = (Map<String, Object>) schema.get(Constants.PROPERTIES);
        attributes = new HashSet<AttributeInfoHelper>(properties.size());
        Set<String> defaultAttributes = new HashSet<String>(properties.size());
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            AttributeInfoHelper helper = new AttributeInfoHelper(e.getKey(), false, (Map<String, Object>) e.getValue());
            if (helper.getAttributeInfo().getName().equals(Name.NAME)) {
                nameAttribute = e.getKey();
            }
            if (helper.getAttributeInfo().isReturnedByDefault()) {
                defaultAttributes.add(helper.getAttributeInfo().getName());
            }
            attributes.add(helper);
        }
        attributesReturnedByDefault = CollectionUtil.newReadOnlySet(defaultAttributes);
    }

    /**
     * Get a new instance of the {@link org.identityconnectors.framework.common.objects.ObjectClass} for this schema.
     *
     * @return new instance of {@link org.identityconnectors.framework.common.objects.ObjectClass}
     */
    public ObjectClass getObjectClass() {
        return objectClass;
    }

    /**
     * Get a read only set of attributes should return by default.
     * <p/>
     * If the {@link OperationOptions#OP_ATTRIBUTES_TO_GET} attribute value is null this is the default always.
     *
     * @return set of attribute names to get for the object.
     */
    public Set<String> getAttributesReturnedByDefault() {
        return attributesReturnedByDefault;
    }

    /**
     * @param operation
     * @param source
     * @param cryptoService
     * @return
     * @throws JsonResourceException if ID value can not be determined from the {@code source}
     */
    public Pair<ObjectClass, Set<Attribute>> build(Class<? extends APIOperation> operation, JsonValue source, CryptoService cryptoService) throws Exception {
        String nameValue = null;

        JsonValue o = source.get(nameAttribute);
        if (o.isString()) {
            nameValue = Id.unescapeUid(o.asString());
        }

        if (CreateApiOp.class.isAssignableFrom(operation) && StringUtil.isBlank(o.asString())) {
            throw new JsonResourceException(JsonResourceException.BAD_REQUEST, "Required NAME attribute is missing");
        }
        logger.trace("Build ConnectorObject {} for {}", nameValue, operation.getSimpleName());


        Map<String, Attribute> builder = new HashMap<String, Attribute>();
        Set<String> keySet = source.required().asMap().keySet();
        if (CreateApiOp.class.isAssignableFrom(operation)) {
            builder.put(Name.NAME, new Name(nameValue));
            for (AttributeInfoHelper attributeInfo : attributes) {
                if (Name.NAME.equals(attributeInfo.getName()) || Uid.NAME.equals(attributeInfo.getName()) ||
                        !keySet.contains(attributeInfo.getName())) {
                    continue;
                }
                if (attributeInfo.getAttributeInfo().isCreateable()) {
                    Object v = source.get(attributeInfo.getName());
                    if (null == v && attributeInfo.getAttributeInfo().isRequired()) {
                        throw new IllegalArgumentException("Required attribute {" + attributeInfo.getName() + "} value is null");
                    }
                    builder.put(attributeInfo.getName(), attributeInfo.build(v, cryptoService));
                }
            }
        } else if (UpdateApiOp.class.isAssignableFrom(operation)) {
            if (null != nameValue) {
                builder.put(Name.NAME, new Name(nameValue));
            }
            for (AttributeInfoHelper attributeInfo : attributes) {
                if (Name.NAME.equals(attributeInfo.getName()) || Uid.NAME.equals(attributeInfo.getName()) ||
                        !keySet.contains(attributeInfo.getName())) {
                    continue;
                }
                if (attributeInfo.getAttributeInfo().isUpdateable()) {
                    Object v = source.get(attributeInfo.getName());
                    builder.put(attributeInfo.getName(), attributeInfo.build(v, cryptoService));
                }
            }
        } else {
            for (AttributeInfoHelper attributeInfo : attributes) {
                if (Name.NAME.equals(attributeInfo.getName()) || Uid.NAME.equals(attributeInfo.getName()) ||
                        !keySet.contains(attributeInfo.getName())) {
                    continue;
                }
                Object v = source.get(attributeInfo.getName());
                builder.put(attributeInfo.getName(), attributeInfo.build(v, cryptoService));
            }
        }
        return new Pair<ObjectClass, Set<Attribute>>(objectClass, new HashSet<Attribute>(builder.values()));
    }

    public JsonValue build(ConnectorObject source, CryptoService cryptoService) throws IOException, JsonCryptoException {
        if (null == source) {
            return null;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("ConnectorObject source: {}", SerializerUtil.serializeXmlObject(source, false));
        }
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(source.getAttributes().size()));
        for (AttributeInfoHelper attributeInfo : attributes) {
            Attribute attribute = source.getAttributeByName(attributeInfo.getAttributeInfo().getName());
            if (null != attribute) {
                result.put(attributeInfo.getName(), attributeInfo.build(attribute, cryptoService));
            }
        }
        result.put(ServerConstants.OBJECT_PROPERTY_ID, Id.escapeUid(source.getUid().getUidValue()));
        if (null != source.getUid().getRevision()) {
            //System supports Revision
            result.put(ServerConstants.OBJECT_PROPERTY_REV, source.getUid().getRevision());
        }
        return result;
    }

    public Attribute build(String attributeName, Object source, CryptoService cryptoService) throws Exception {
        for (AttributeInfoHelper attributeInfoHelper : attributes) {
            if (attributeInfoHelper.getName().equals(attributeName)) {
                return attributeInfoHelper.build(source, cryptoService);
            }
        }
        if (source instanceof Collection) {
            return AttributeBuilder.build(attributeName, (Collection) source);
        } else {
            return AttributeBuilder.build(attributeName, source);
        }
    }
}
