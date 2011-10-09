package org.juzu.portlet;

import org.juzu.URLBuilder;

import javax.portlet.PortletURL;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
class PortletURLBuilder implements URLBuilder
{

   /** . */
   private final PortletURL url;

   PortletURLBuilder(PortletURL url)
   {
      this.url = url;
   }

   public URLBuilder setParameter(String name, String value)
   {
      url.setParameter(name, value);
      return this;
   }

   @Override
   public String toString()
   {
      return url.toString();
   }
}
