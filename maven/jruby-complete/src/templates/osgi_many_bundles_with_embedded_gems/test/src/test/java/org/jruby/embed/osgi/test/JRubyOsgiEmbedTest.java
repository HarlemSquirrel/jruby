/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.embed.osgi.test;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import org.jruby.embed.IsolatedScriptingContainer;
import org.jruby.embed.osgi.OSGiIsolatedScriptingContainer;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.BundleContext;

import org.jruby.osgi.gems.Gems;
import org.jruby.osgi.scripts.Scripts;

/**
 * @author ajuckel
 */
@RunWith(PaxExam.class)
public class JRubyOsgiEmbedTest {

    @Configuration
    public Option[] config() {
        return options(
            junitBundles(),
            systemProperty("org.ops4j.pax.url.mvn.localRepository").value(System.getProperty( "maven.repo.local" )),
            mavenBundle("org.jruby", "jruby-complete", System.getProperty("jruby.version")),
            mavenBundle("org.jruby.osgi", "gems-bundle", "1.0"),
            mavenBundle("org.jruby.osgi", "scripts-bundle", "1.0")
        );
    }

    @Test
    public void testJRubyWithoutOSGiApi() throws Exception {

        System.err.println();
        System.err.println();

        //System.setProperty( "jruby.debug.loadService", "true" );
        //System.setProperty( "jruby.native.enabled", "true" );

        IsolatedScriptingContainer jruby = new IsolatedScriptingContainer();
        jruby.addClassLoader( Gems.class.getClassLoader() );
	jruby.addClassLoader( Scripts.class.getClassLoader() );

        // run a script from LOAD_PATH
        String hello = (String) jruby.runScriptlet( "require 'hello'; Hello.say" );
        assertEquals( "world", hello );

        System.err.println();
        System.err.println();

        String gemPath = (String) jruby.runScriptlet( "Gem::Specification.dirs.inspect" );
        gemPath = gemPath.replaceAll( "bundle[^:]*://[^/]*", "bundle:/" );
        assertEquals( gemPath, "[\"uri:bundle://specifications\"]" );

        // ensure we can load rake from the default gems
        boolean loaded = (Boolean) jruby.runScriptlet( "require 'rake'" );
        assertEquals(true, loaded);

        String list = (String) jruby.runScriptlet( "Gem.loaded_specs.keys.inspect" );
        assertEquals( "[\"rake\"]", list );

        // ensure we have native working
        loaded = (Boolean) jruby.runScriptlet( "JRuby.runtime.posix.is_native" );
        assertEquals(true, loaded);

        // ensure we can load openssl (with its bouncy-castle jars)
        loaded = (Boolean) jruby.runScriptlet( "require 'openssl'" );
        assertEquals(true, loaded);

        jruby.runScriptlet( "require 'jar-dependencies'" );

        assertGemListEquals(jruby, "jar-dependencies", "jruby-openssl", "rake");

        // ensure we can load can load embedded gems
        loaded = (Boolean) jruby.runScriptlet( "require 'virtus'" );
        assertEquals(true, loaded);

        assertGemListEquals(jruby, "axiom-types", "coercible", "descendants_tracker", "equalizer", "ice_nine", "jar-dependencies", "jruby-openssl", "rake", "thread_safe", "virtus");
    }

    @Test
    public void testJRubyWithOSGiApi() throws Exception {

        System.err.println();
        System.err.println();

        //System.setProperty( "jruby.debug.loadService", "true" );
        //System.setProperty( "jruby.native.enabled", "true" );

        OSGiIsolatedScriptingContainer jruby = new OSGiIsolatedScriptingContainer();
        jruby.addBundle( "org.jruby.osgi.scripts-bundle" );
        jruby.addBundle( FrameworkUtil.getBundle( Gems.class ) );

        // run a script from LOAD_PATH
        String hello = (String) jruby.runScriptlet( "require 'hello'; Hello.say" );
        assertEquals( "world", hello );

        System.err.println();
        System.err.println();

        String gemPath = (String) jruby.runScriptlet( "Gem::Specification.dirs.inspect" );
        gemPath = gemPath.replaceAll( "bundle[^:]*://[^/]*", "bundle:/" );
        assertEquals( "[\"uri:bundle://specifications\"]", gemPath );

        // ensure we can load rake from the default gems
        boolean loaded = (Boolean) jruby.runScriptlet( "require 'rake'" );
        assertEquals(true, loaded);

        String list = (String) jruby.runScriptlet( "Gem.loaded_specs.keys.inspect" );
        assertEquals( "[\"rake\"]", list );

        // ensure we have native working
        loaded = (Boolean) jruby.runScriptlet( "JRuby.runtime.posix.is_native" );
        assertEquals(true, loaded);

        // ensure we can load openssl (with its bouncy-castle jars)
        loaded = (Boolean) jruby.runScriptlet( "require 'openssl'" );
        assertEquals(true, loaded);

        jruby.runScriptlet( "require 'jar-dependencies'" );

        assertGemListEquals(jruby, "jar-dependencies", "jruby-openssl", "rake");

        // ensure we can load can load embedded gems
        loaded = (Boolean) jruby.runScriptlet( "require 'virtus'" );
        assertEquals(true, loaded);

        assertGemListEquals(jruby, "axiom-types", "coercible", "descendants_tracker", "equalizer", "ice_nine", "jar-dependencies", "jruby-openssl", "rake", "thread_safe", "virtus");
    }

    private static void assertGemListEquals(final IsolatedScriptingContainer jruby, final String... expected) {
        String list = (String) jruby.runScriptlet( "Gem.loaded_specs.keys.sort.join(', ')" );

        Arrays.sort(expected);

        if ( gemJOpenSSLPreRelease(jruby) ) {
            ArrayList<String> tmp = new ArrayList<String>(Arrays.asList(expected));
            tmp.remove("jruby-openssl"); // pre-release gem not reported in loaded_keys

            for ( String name : tmp.toArray(new String[0]) ) {
                assertThat(list, containsString(name));
            }
        }
        else {
            assertEquals( Arrays.toString(expected), '[' + list + ']' );
        }
    }

    private static boolean gemJOpenSSLPreRelease(final IsolatedScriptingContainer jruby) {
        String josslVersion = (String) jruby.runScriptlet( "require 'jopenssl/version'; Jopenssl::Version::VERSION" );
        return josslVersion.matches(".*?[a-zA-Z]");
    }

}
