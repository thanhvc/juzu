package org.juzu.impl.application.metamodel;

import org.juzu.impl.application.metadata.ApplicationDescriptor;
import org.juzu.impl.compiler.CompilationException;
import org.juzu.impl.model.CompilationErrorCode;
import org.juzu.impl.model.meta.MetaModel;
import org.juzu.impl.model.meta.MetaModelEvent;
import org.juzu.impl.model.meta.MetaModelObject;
import org.juzu.impl.model.meta.MetaModelPlugin;
import org.juzu.impl.model.processor.ProcessingContext;
import org.juzu.impl.utils.FQN;
import org.juzu.impl.utils.JSON;
import org.juzu.impl.utils.Tools;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class ApplicationPlugin extends MetaModelPlugin
{

   /** . */
   private static final String APPLICATION_DESCRIPTOR = ApplicationDescriptor.class.getSimpleName();

   /** . */
   private Map<String, String> moduleConfig;

   @Override
   public void init(MetaModel model)
   {
      model.addChild(ApplicationsMetaModel.KEY, new ApplicationsMetaModel());
      moduleConfig = new HashMap<String, String>();
   }

   @Override
   public void processAnnotation(MetaModel model, Element element, String annotationFQN, Map<String, Object> annotationValues) throws CompilationException
   {
      if (annotationFQN.equals("org.juzu.Application"))
      {
         MetaModel.log.log("Processing application " + element);
         model.getChild(ApplicationsMetaModel.KEY).processApplication((PackageElement)element, annotationFQN, annotationValues);
      }
   }

   @Override
   public void processEvent(MetaModel model, MetaModelEvent event)
   {
      MetaModelObject obj = event.getObject();
      if (obj instanceof ApplicationMetaModel)
      {
         ApplicationMetaModel application = (ApplicationMetaModel)obj;
         if (event.getType() == MetaModelEvent.AFTER_ADD)
         {
            moduleConfig.put(application.getFQN().getSimpleName(), application.getFQN().getFullName());
            emitApplication(model.env, application);
         }
         else if (event.getType() == MetaModelEvent.BEFORE_REMOVE)
         {
            // Should we do something
         }
      }
   }

   @Override
   public void prePassivate(MetaModel model)
   {
      MetaModel.log.log("Emitting config");
      emitConfig(model);
   }

   private void emitApplication(ProcessingContext env, ApplicationMetaModel application) throws CompilationException
   {
      PackageElement elt = env.get(application.getHandle());
      FQN fqn = application.getFQN();

      //
      Writer writer = null;
      try
      {
         JavaFileObject applicationFile = env.createSourceFile(fqn.getFullName(), elt);
         writer = applicationFile.openWriter();

         writer.append("package ").append(fqn.getPackageName()).append(";\n");

         // Imports
         writer.append("import ").append(Tools.getImport(ApplicationDescriptor.class)).append(";\n");

         // Open class
         writer.append("public class ").append(fqn.getSimpleName()).append(" {\n");

         // Singleton
         writer.append("public static final ").append(APPLICATION_DESCRIPTOR).append(" DESCRIPTOR = new ").append(APPLICATION_DESCRIPTOR).append("(");
         writer.append(fqn.getSimpleName()).append(".class");
         writer.append(",\n");
         writer.append(application.getDefaultController() != null ? (application.getDefaultController() + ".class") : "null");
         writer.append(",\n");
         writer.append(application.getEscapeXML() != null ? Boolean.toString(application.getEscapeXML()) : "null");
         writer.append(",\n");
         writer.append("\"").append(application.getTemplates().getQN()).append("\"");
         writer.append(",\n");
         writer.append("java.util.Arrays.<Class<? extends org.juzu.plugin.Plugin>>asList(");
         List<FQN> plugins = application.getPlugins();
         for (int i = 0;i < plugins.size();i++)
         {
            if (i > 0)
            {
               writer.append(',');
            }
            FQN plugin = plugins.get(i);
            writer.append(plugin.getFullName()).append(".class");
         }
         writer.append(")");
         writer.append(");\n");

         // Close class
         writer.append("}\n");

         //
         MetaModel.log.log("Generated application " + fqn.getFullName() + " as " + applicationFile.toUri());
      }
      catch (IOException e)
      {
         throw new CompilationException(e, elt, CompilationErrorCode.CANNOT_WRITE_APPLICATION, application.getFQN());
      }
      finally
      {
         Tools.safeClose(writer);
      }
   }

   private void emitConfig(MetaModel model)
   {
      JSON json = new JSON();
      json.add(moduleConfig);

      // Module config
      Writer writer = null;
      try
      {
         //
         FileObject fo = model.env.createResource(StandardLocation.CLASS_OUTPUT, "org.juzu", "config.json");
         writer = fo.openWriter();
         json.toString(writer);
      }
      catch (IOException e)
      {
         throw new CompilationException(e, CompilationErrorCode.CANNOT_WRITE_CONFIG);
      }
      finally
      {
         Tools.safeClose(writer);
      }

      // Application configs
      for (ApplicationMetaModel application : model.getChild(ApplicationsMetaModel.KEY))
      {
         json.clear();

         // Emit config
         for (MetaModelPlugin plugin : model.getPlugins())
         {
            plugin.emitConfig(application, json);
         }

         //
         writer = null;
         try
         {
            FileObject fo = model.env.createResource(StandardLocation.CLASS_OUTPUT, application.getFQN().getPackageName(), "config.json");
            writer = fo.openWriter();
            json.toString(writer);
         }
         catch (IOException e)
         {
            throw new CompilationException(e, model.env.get(application.getHandle()), CompilationErrorCode.CANNOT_WRITE_APPLICATION_CONFIG, application.getFQN());
         }
         finally
         {
            Tools.safeClose(writer);
         }
      }
   }
}
