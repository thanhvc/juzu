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

package org.juzu.impl.spi.inject.spring;

import org.juzu.impl.inject.BeanFilter;
import org.juzu.impl.inject.ScopeController;
import org.juzu.impl.request.Scope;
import org.juzu.impl.spi.fs.ReadFileSystem;
import org.juzu.impl.spi.inject.InjectBuilder;
import org.juzu.impl.spi.inject.InjectManager;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.CustomAutowireConfigurer;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.context.annotation.ScopeMetadata;
import org.springframework.core.io.UrlResource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class SpringBuilder extends InjectBuilder
{

   /** . */
   private ClassLoader classLoader;

   /** . */
   private Map<String, AbstractBean> beans = new LinkedHashMap<String, AbstractBean>();

   /** . */
   private Set<Scope> scopes = new LinkedHashSet<Scope>();

   /** . */
   private URL configurationURL;

   public URL getConfigurationURL()
   {
      return configurationURL;
   }

   public void setConfigurationURL(URL configurationURL)
   {
      this.configurationURL = configurationURL;
   }

   @Override
   public InjectBuilder setFilter(BeanFilter filter)
   {
      return this;
   }

   @Override
   public <T> InjectBuilder declareBean(Class<T> type, Iterable<Annotation> qualifiers, Class<? extends T> implementationType)
   {
      if (implementationType == null)
      {
         implementationType = type;
      }

      //
      String name = "" + Math.random();
      for (Annotation annotation : implementationType.getDeclaredAnnotations())
      {
         if (annotation instanceof Named)
         {
            Named named = (Named)annotation;
            name = named.value();
            break;
         }
      }

      //
      beans.put(name, new DeclaredBean(implementationType, qualifiers));
      return this;
   }

   @Override
   public <T> InjectBuilder bindBean(Class<T> type, Iterable<Annotation> qualifiers, T instance)
   {
      String name = "" + Math.random();
      beans.put(name, new SingletonBean(instance, qualifiers));
      return this;
   }

   @Override
   public <T> InjectBuilder bindProvider(Class<T> beanType, Iterable<Annotation> beanQualifiers, final Provider<T> provider)
   {
      return bindBean(ProviderFactory.class, beanQualifiers, new ProviderFactory<T>(beanType)
      {
         @Override
         public Provider<T> getProvider()
         {
            return provider;
         }
      });
   }

   @Override
   public <T> InjectBuilder declareProvider(Class<T> type, Iterable<Annotation> qualifiers, final Class<? extends Provider<T>> provider)
   {
      return bindBean(ProviderFactory.class, qualifiers, new ProviderFactory<T>(type)
      {
         @Override
         public Provider<T> getProvider() throws Exception
         {
            return provider.newInstance();
         }
      });
   }

   @Override
   public <P> InjectBuilder addFileSystem(ReadFileSystem<P> fs)
   {
      return this;
   }

   @Override
   public InjectBuilder addScope(Scope scope)
   {
      scopes.add(scope);
      return this;
   }

   @Override
   public InjectBuilder setClassLoader(ClassLoader classLoader)
   {
      this.classLoader = classLoader;
      return this;
   }

   @Override
   public <B, I> InjectManager<B, I> create() throws Exception
   {
      DefaultListableBeanFactory factory;
      if (configurationURL != null)
      {
         factory = new XmlBeanFactory(new UrlResource(configurationURL));
      }
      else
      {
         factory = new DefaultListableBeanFactory();
      }

      //
      factory.setBeanClassLoader(classLoader);
      factory.setInstantiationStrategy(new SingletonInstantiationStrategy(new CglibSubclassingInstantiationStrategy(), beans));

      // Register scopes
      for (Scope scope : scopes)
      {
         factory.registerScope(scope.name().toLowerCase(), new SpringScope(factory, scope, ScopeController.INSTANCE));
      }

      //
      ScopeMetadataResolverImpl resolver = new ScopeMetadataResolverImpl(scopes);
      for (Map.Entry<String, AbstractBean> entry : beans.entrySet())
      {
         AbstractBean bean = entry.getValue();

         // Scope
         String scopeName;
         AnnotatedGenericBeanDefinition definition = new AnnotatedGenericBeanDefinition(bean.type);
         if (bean instanceof SingletonBean)
         {
            scopeName = "singleton";
         }
         else
         {
            ScopeMetadata scopeMD = resolver.resolveScopeMetadata(definition);
            scopeName = scopeMD != null ? scopeMD.getScopeName() : null;
         }
         if (scopeName != null)
         {
            definition.setScope(scopeName);
         }
         
         // Qualifiers
         if (bean.qualifiers != null)
         {
            for (AutowireCandidateQualifier qualifier : bean.qualifiers)
            {
               definition.addQualifier(qualifier);
            }
         }

         //
         factory.registerBeanDefinition(entry.getKey(), definition);
      }

      //
      AutowiredAnnotationBeanPostProcessor beanPostProcessor = new AutowiredAnnotationBeanPostProcessor();
      beanPostProcessor.setAutowiredAnnotationType(Inject.class);
      beanPostProcessor.setBeanFactory(factory);
      factory.addBeanPostProcessor(beanPostProcessor);

      //
      CommonAnnotationBeanPostProcessor commonAnnotationBeanProcessor = new CommonAnnotationBeanPostProcessor();
      factory.addBeanPostProcessor(commonAnnotationBeanProcessor);

      //
      Set cqt = new HashSet();
      cqt.add(Named.class);
      CustomAutowireConfigurer configurer = new CustomAutowireConfigurer();
      configurer.setCustomQualifierTypes(cqt);
      QualifierAnnotationAutowireCandidateResolver customResolver = new QualifierAnnotationAutowireCandidateResolver();
      factory.setAutowireCandidateResolver(customResolver);
      configurer.postProcessBeanFactory(factory);

      //
      return (InjectManager<B, I>)new SpringManager(factory, classLoader);
   }
}
