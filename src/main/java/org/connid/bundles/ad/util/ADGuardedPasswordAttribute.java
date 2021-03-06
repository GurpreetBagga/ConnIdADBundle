/**
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 * Copyright 2011-2013 Tirasa. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License"). You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at https://oss.oracle.com/licenses/CDDL
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at https://oss.oracle.com/licenses/CDDL.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.connid.bundles.ad.util;

import java.io.UnsupportedEncodingException;

import java.util.List;
import javax.naming.directory.BasicAttribute;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.OperationalAttributes;

public abstract class ADGuardedPasswordAttribute {

    private static final Log LOG = Log.getLog(ADGuardedPasswordAttribute.class);

    public static ADGuardedPasswordAttribute create(
            final String attrName, final Attribute attr) {

        assert attr.is(OperationalAttributes.PASSWORD_NAME);

        final List<Object> value = attr.getValue();

        if (value != null && !value.isEmpty()) {
            return ADGuardedPasswordAttribute.create(attrName,
                    (GuardedString) value.get(0));
        } else {
            return ADGuardedPasswordAttribute.create(attrName);
        }
    }

    public static ADGuardedPasswordAttribute create(
            final String attrName, final GuardedString password) {
        return new Simple(attrName, password);
    }

    public static ADGuardedPasswordAttribute create(final String attrName) {
        return new Empty(attrName);
    }

    public abstract void access(final Accessor accessor);

    public interface Accessor {

        void access(BasicAttribute passwordAttribute);
    }

    private static final class Simple extends ADGuardedPasswordAttribute {

        private final String attrName;

        private final GuardedString password;

        private Simple(String attrName, GuardedString password) {
            this.attrName = attrName;
            this.password = password;
        }

        @Override
        public void access(final Accessor accessor) {
            password.access(new GuardedString.Accessor() {

                @Override
                public void access(char[] clearChars) {
                    final String quotedPwd = "\"" + new String(clearChars) + "\"";

                    try {

                        byte[] unicodePwd = quotedPwd.getBytes("UTF-16LE");

                        final BasicAttribute attr =
                                new BasicAttribute(attrName, unicodePwd);

                        accessor.access(attr);

                    } catch (UnsupportedEncodingException e) {
                        LOG.error(e, "Error encoding password");
                    }
                }
            });
        }
    }

    private static final class Empty extends ADGuardedPasswordAttribute {

        private final String attrName;

        private Empty(String attrName) {
            this.attrName = attrName;
        }

        @Override
        public void access(Accessor accessor) {
            accessor.access(new BasicAttribute(attrName));
        }
    }
}