package org.juzu.impl.spi.inject.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.InstantiationStrategy;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
class SingletonInstantiationStrategy implements InstantiationStrategy
{

   /** . */
   private final Map<String, AbstractBean> singletons;

   /** . */
   private final InstantiationStrategy delegate;

   SingletonInstantiationStrategy(InstantiationStrategy delegate, Map<String, AbstractBean> singletons)
   {
      this.delegate = delegate;
      this.singletons = singletons;
   }

   public Object instantiate(RootBeanDefinition beanDefinition, String beanName, BeanFactory owner) throws BeansException
   {
      AbstractBean bean = singletons.get(beanName);
      if (bean instanceof SingletonBean)
      {
         return ((SingletonBean)bean).instance;
      }
      return delegate.instantiate(beanDefinition, beanName, owner);
   }

   public Object instantiate(RootBeanDefinition beanDefinition, String beanName, BeanFactory owner, Constructor<?> ctor, Object[] args) throws BeansException
   {
      AbstractBean bean = singletons.get(beanName);
      if (bean instanceof SingletonBean)
      {
         return ((SingletonBean)bean).instance;
      }
      return delegate.instantiate(beanDefinition, beanName, owner, ctor, args);
   }

   public Object instantiate(RootBeanDefinition beanDefinition, String beanName, BeanFactory owner, Object factoryBean, Method factoryMethod, Object[] args) throws BeansException
   {
      AbstractBean bean = singletons.get(beanName);
      if (bean instanceof SingletonBean)
      {
         return ((SingletonBean)bean).instance;
      }
      return delegate.instantiate(beanDefinition, beanName, owner, factoryBean, factoryMethod, args);
   }
}
