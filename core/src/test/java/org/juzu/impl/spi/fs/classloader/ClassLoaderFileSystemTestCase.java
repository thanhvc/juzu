/*
 * Copyright (C) 2011 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.juzu.impl.spi.fs.classloader;

import junit.framework.TestCase;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.juzu.impl.utils.Tools;
import sun.net.www.protocol.foo.Handler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class ClassLoaderFileSystemTestCase extends TestCase
{

   /** . */
   private JavaArchive jar;

   {
      JavaArchive jar = ShrinkWrap.create(JavaArchive.class);
      jar.addAsResource(new StringAsset("bar.txt_value"), "bar.txt");
      jar.addAsResource(new StringAsset("foo/bar.txt_value"), "foo/bar.txt");
      jar.addAsResource(new StringAsset("foo/bar/juu.txt_value"), "foo/bar/juu.txt");

      //
      this.jar = jar;
   }

   public void testJarFile() throws Exception
   {
      File f = File.createTempFile("test", ".jar");
      f.deleteOnExit();
      jar.as(ZipExporter.class).exportTo(f, true);
      assertFS(f.toURI().toURL());
   }

   public void testJarStream() throws Exception
   {
      try
      {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         jar.as(ZipExporter.class).exportTo(baos);
         byte[] bytes = baos.toByteArray();
         Handler.bind("jarfile", bytes);
         URL url = new URL("foo:jarfile");

         //
         final String abc = url.toString();
         final URL fooURL = new URL("jar:" + abc + "!/foo/");
         final URL barTxtURL = new URL("jar:" + abc + "!/foo/bar.txt");
         final URL barURL = new URL("jar:" + abc + "!/foo/bar/");
         final URL juuTxtURL = new URL("jar:" + abc + "!/foo/bar/juu.txt");

         //
         ClassLoader cl = new ClassLoader(ClassLoader.getSystemClassLoader())
         {
            @Override
            protected URL findResource(String name)
            {
               if ("foo/".equals(name))
               {
                  return fooURL;
               }
               else if ("foo/bar.txt".equals(name))
               {
                  return barTxtURL;
               }
               else if ("foo/bar/juu.txt".equals(name))
               {
                  return barURL;
               }
               else if ("foo/bar/juu.txt".equals(name))
               {
                  return juuTxtURL;
               }
               return null;
            }

            @Override
            protected Enumeration<URL> findResources(String name) throws IOException
            {
               Vector<URL> v = new Vector<URL>();
               URL url = findResource(name);
               if (url != null)
               {
                  v.add(url);
               }
               return v.elements();
            }
         };

         assertFS(cl);
      }
      finally
      {
         Handler.clear();
      }
   }

   public void testFile() throws Exception
   {
      File f = File.createTempFile("test", "");
      assertTrue(f.delete());
      assertTrue(f.mkdirs());
      f.deleteOnExit();
      File dir = jar.as(ExplodedExporter.class).exportExploded(f);
      assertFS(dir.toURI().toURL());
   }

   private void assertFS(URL base) throws Exception
   {
      assertFS(new URLClassLoader(new URL[]{base}, ClassLoader.getSystemClassLoader()));
   }

   private void assertFS(ClassLoader classLoader) throws Exception
   {
      ClassLoaderFileSystem fs = new ClassLoaderFileSystem(classLoader);

      //
      String foo = fs.getPath("foo");
      assertEquals("foo/", foo);
      assertEquals("foo", fs.getName(foo));
      assertEquals("foo", fs.packageOf(foo, '.', new StringBuilder()).toString());
      assertEquals(Arrays.asList("foo/bar.txt"), Tools.list(fs.getChildren(foo)));

      //
      String fooBar = fs.getPath("foo", "bar.txt");
      assertEquals("foo/bar.txt", fooBar);
      assertEquals("bar.txt", fs.getName(fooBar));
      assertEquals("foo", fs.packageOf(fooBar, '.', new StringBuilder()).toString());

      //
      String fooBarJuu = fs.getPath("foo", "bar", "juu.txt");
      assertEquals("foo/bar/juu.txt", fooBarJuu);
      assertEquals("juu.txt", fs.getName(fooBarJuu));
      assertEquals("foo.bar", fs.packageOf(fooBarJuu, '.', new StringBuilder()).toString());

      //
      assertEquals(null, fs.getPath("juu"));
   }
}
