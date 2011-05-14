/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 */

package org.forgerock.openidm.router;

// Java Standard Edition
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

// OSGi Framework
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

// Apache Felix Maven SCR Plugin
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;

// ForgeRock OpenIDM
import org.forgerock.openidm.objset.ObjectSet;
import org.forgerock.openidm.objset.ObjectSetRouter;

/**
 * Provides internal routing for a top-level object set.
 *
 * @author Paul C. Bryan
 */
@Component(
    name="org.forgerock.openidm.router",
    policy=ConfigurationPolicy.IGNORE
)
@Properties({
    @Property(name="service.description", value="OpenIDM internal object set router"),
    @Property(name="service.vendor", value="ForgeRock AS")
})
@Service
public class ObjectSetRouterService extends ObjectSetRouter {

    /** TODO: Description. */
    private static final String REFERENCE_NAME = "reference_ObjectSetRouterService_ObjectSet";

    /** TODO: Description. */
    private final HashSet<ServiceReference> references = new HashSet<ServiceReference>();

    /** TODO: Description. */
    private ComponentContext context;

    @Reference(
        name=REFERENCE_NAME,
        referenceInterface=ObjectSet.class,
        bind="bind",
        unbind="unbind",
        cardinality=ReferenceCardinality.OPTIONAL_MULTIPLE,
        policy=ReferencePolicy.DYNAMIC,
        strategy=ReferenceStrategy.EVENT
    )
    protected int _dummy; // whiteboard pattern
    protected synchronized void bind(ServiceReference reference) {
        references.add(reference);
        refresh();
    }
    protected synchronized void unbind(ServiceReference reference) {
        references.remove(reference);
        refresh();
    }

    @Activate
    protected synchronized void activate(ComponentContext context) {
        this.context = context;
        refresh();
    }
    @Deactivate
    protected synchronized void deactivate(ComponentContext context) {
        this.context = null;
        refresh();
    }

    /**
     * TODO: Description.
     */
    private void refresh() {
        if (context != null) {
            HashMap<String, ObjectSet> refreshed = new HashMap<String, ObjectSet>();
            for (ServiceReference reference : references) {
                Object prefix = reference.getProperty("openidm.router.prefix");
                if (prefix != null && prefix instanceof String) { // service is specified as internally routable
                    refreshed.put((String)prefix, (ObjectSet)context.locateService(REFERENCE_NAME, reference));
                }
            }
            routes = refreshed;
        }
        else {
            routes.clear();
        }
    }
}