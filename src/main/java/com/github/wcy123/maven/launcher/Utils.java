package com.github.wcy123.maven.launcher;
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU General Public License
 * Version 2 only ("GPL") or the Common Development and Distribution License("CDDL") (collectively,
 * the "License"). You may not use this file except in compliance with the License. You can obtain a
 * copy of the License at http://www.netbeans.org/cddl-gplv2.html or nbbuild/licenses/CDDL-GPL-2-CP.
 * See the License for the specific language governing permissions and limitations under the
 * License. When distributing the software, include this License Header Notice in each file and
 * include the License file at nbbuild/licenses/CDDL-GPL-2-CP. Sun designates this particular file
 * as subject to the "Classpath" exception as provided by Sun in the GPL Version 2 section of the
 * License file that accompanied this code. If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original Software is Sun
 * Microsystems, Inc. Portions Copyright 1997-2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL or only the GPL Version 2,
 * indicate your decision by adding "[Contributor] elects to include this software in this
 * distribution under the [CDDL or GPL Version 2] license." If you do not indicate a single choice
 * of license, a recipient has the option to distribute your version of this file under either the
 * CDDL, the GPL Version 2 or to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL Version 2 license, then the
 * option applies only if the new code is made subject to such option by the copyright holder.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * @since 4.37
 * @author Jaroslav Tulach
 */

public class Utils {
    /**
     * Concatenates the content of two enumerations into one. Until the end of <code>en1</code> is
     * reached its elements are being served. As soon as the <code>en1</code> has no more elements,
     * the content of <code>en2</code> is being returned.
     *
     * @param en1 first enumeration
     * @param en2 second enumeration
     * @return enumeration
     */
    public static <T> Enumeration<T> concat(Enumeration<? extends T> en1,
            Enumeration<? extends T> en2) {
        ArrayList<Enumeration<? extends T>> two = new ArrayList<Enumeration<? extends T>>();
        two.add(en1);
        two.add(en2);
        return new SeqEn<T>(Collections.enumeration(two));
    }

}


class SeqEn<T> extends Object implements Enumeration<T> {
    /** enumeration of Enumerations */
    private Enumeration<? extends Enumeration<? extends T>> en;

    /** current enumeration */
    private Enumeration<? extends T> current;

    /**
     * is {@link #current} up-to-date and has more elements? The combination
     * <CODE>current == null</CODE> and <CODE>checked == true means there are no more elements in
     * this enumeration.
     */
    private boolean checked = false;

    /**
     * Constructs new enumeration from already existing. The elements of <CODE>en</CODE> should be
     * also enumerations. The resulting enumeration contains elements of such enumerations.
     *
     * @param en enumeration of Enumerations that should be sequenced
     */
    public SeqEn(Enumeration<? extends Enumeration<? extends T>> en) {
        this.en = en;
    }

    /**
     * Ensures that current enumeration is set. If there aren't more elements in the Enumerations,
     * sets the field <CODE>current</CODE> to null.
     */
    private void ensureCurrent() {
        while ((current == null) || !current.hasMoreElements()) {
            if (en.hasMoreElements()) {
                current = en.nextElement();
            } else {
                // no next valid enumeration
                current = null;

                return;
            }
        }
    }

    /** @return true if we have more elements */
    public boolean hasMoreElements() {
        if (!checked) {
            ensureCurrent();
            checked = true;
        }

        return current != null;
    }

    /**
     * @return next element
     * @exception NoSuchElementException if there is no next element
     */
    public T nextElement() {
        if (!checked) {
            ensureCurrent();
        }

        if (current != null) {
            checked = false;

            return current.nextElement();
        } else {
            checked = true;
            throw new java.util.NoSuchElementException();
        }
    }
}
