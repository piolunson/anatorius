package dev.project.anatorius.customsky.client.compat;

import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.project.anatorius.customsky.Customskyboxes;

@Environment(EnvType.CLIENT)
public final class IrisCompat {
   private IrisCompat() {
   }

   public static void assign(Object pipeline, String program) {
      try {
         Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
         Class programs = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
         Object instance = api.getMethod("getInstance").invoke((Object)null);

         for(Method method : api.getMethods()) {
            if (method.getName().equals("assignPipeline") && method.getParameterCount() == 2) {
               method.invoke(instance, pipeline, Enum.valueOf(programs, program));
               return;
            }
         }
      } catch (ClassNotFoundException var9) {
      } catch (Throwable failed) {
         Customskyboxes.LOGGER.warn("Could not register the sky with Iris: {}", failed.toString());
      }
   }
}
