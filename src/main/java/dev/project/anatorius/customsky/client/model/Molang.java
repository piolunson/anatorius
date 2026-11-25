package dev.project.anatorius.customsky.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Molang {
   private Molang() {
   }

   public static Expr constant(double value) {
      return (scope) -> value;
   }

   public static Expr parse(String text) {
      try {
         String s = text.trim().toLowerCase();
         if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1);
         }

         if (s.startsWith("return ")) {
            s = s.substring(7);
         }

         try {
            return constant(Double.parseDouble(s));
         } catch (NumberFormatException var4) {
            Parser p = new Parser(s);
            Expr e = p.sum();
            return p.pos < s.length() ? constant((double)0.0F) : e;
         }
      } catch (RuntimeException var5) {
         return constant((double)0.0F);
      }
   }

   private static Expr variable(String name) {
      Expr var10000;
      switch (name) {
         case "query.anim_time":
         case "q.anim_time":
            var10000 = (sc) -> sc.animTime;
            break;
         case "query.life_time":
         case "q.life_time":
            var10000 = (sc) -> sc.lifeTime;
            break;
         case "math.pi":
            var10000 = constant(Math.PI);
            break;
         default:
            var10000 = constant((double)0.0F);
      }

      return var10000;
   }

   private static Expr function(String name, List<Expr> a) {
      Expr x = a.isEmpty() ? constant((double)0.0F) : (Expr)a.get(0);
      Expr y = a.size() > 1 ? (Expr)a.get(1) : constant((double)0.0F);
      Expr z = a.size() > 2 ? (Expr)a.get(2) : constant((double)0.0F);
      DoubleSupplier never = () -> (double)0.0F;
      Expr var10000;
      switch (name) {
         case "math.sin" -> var10000 = (sc) -> Math.sin(Math.toRadians(x.eval(sc)));
         case "math.cos" -> var10000 = (sc) -> Math.cos(Math.toRadians(x.eval(sc)));
         case "math.abs" -> var10000 = (sc) -> Math.abs(x.eval(sc));
         case "math.sqrt" -> var10000 = (sc) -> Math.sqrt(Math.max((double)0.0F, x.eval(sc)));
         case "math.pow" -> var10000 = (sc) -> Math.pow(x.eval(sc), y.eval(sc));
         case "math.floor" -> var10000 = (sc) -> Math.floor(x.eval(sc));
         case "math.ceil" -> var10000 = (sc) -> Math.ceil(x.eval(sc));
         case "math.round" -> var10000 = (sc) -> (double)Math.round(x.eval(sc));
         case "math.trunc" -> var10000 = (sc) -> (double)((long)x.eval(sc));
         case "math.min" -> var10000 = (sc) -> Math.min(x.eval(sc), y.eval(sc));
         case "math.max" -> var10000 = (sc) -> Math.max(x.eval(sc), y.eval(sc));
         case "math.mod" -> var10000 = (sc) -> {
   double d = y.eval(sc);
   return d == (double)0.0F ? (double)0.0F : x.eval(sc) % d;
};
         case "math.clamp" -> var10000 = (sc) -> Math.max(y.eval(sc), Math.min(z.eval(sc), x.eval(sc)));
         case "math.lerp" -> var10000 = (sc) -> x.eval(sc) + (y.eval(sc) - x.eval(sc)) * z.eval(sc);
         case "math.exp" -> var10000 = (sc) -> Math.exp(x.eval(sc));
         case "math.ln" -> var10000 = (sc) -> Math.log(x.eval(sc));
         case "math.atan" -> var10000 = (sc) -> Math.toDegrees(Math.atan(x.eval(sc)));
         case "math.atan2" -> var10000 = (sc) -> Math.toDegrees(Math.atan2(x.eval(sc), y.eval(sc)));
         case "math.random" -> var10000 = (sc) -> x.eval(sc) + Math.random() * (y.eval(sc) - x.eval(sc));
         default -> var10000 = (sc) -> never.getAsDouble();
      }

      return var10000;
   }

   @Environment(EnvType.CLIENT)
   public static final class Scope {
      public double animTime;
      public double lifeTime;
   }

   @Environment(EnvType.CLIENT)
   private static final class Parser {
      final String s;
      int pos;

      Parser(String s) {
         this.s = s;
      }

      void skip() {
         while(this.pos < this.s.length() && this.s.charAt(this.pos) == ' ') {
            ++this.pos;
         }
      }

      boolean eat(char c) {
         this.skip();
         if (this.pos < this.s.length() && this.s.charAt(this.pos) == c) {
            ++this.pos;
            return true;
         } else {
            return false;
         }
      }

      Expr sum() {
         Expr left = this.product();

         while(true) {
            while(!this.eat('+')) {
               if (!this.eat('-')) {
                  return left;
               }

               Expr r = this.product();
               Expr l = left;
               left = (sc) -> l.eval(sc) - r.eval(sc);
            }

            Expr r = this.product();
            Expr l = left;
            left = (sc) -> l.eval(sc) + r.eval(sc);
         }
      }

      Expr product() {
         Expr left = this.unary();

         while(true) {
            while(!this.eat('*')) {
               if (!this.eat('/')) {
                  return left;
               }

               Expr r = this.unary();
               Expr l = left;
               left = (sc) -> {
                  double d = r.eval(sc);
                  return d == (double)0.0F ? (double)0.0F : l.eval(sc) / d;
               };
            }

            Expr r = this.unary();
            Expr l = left;
            left = (sc) -> l.eval(sc) * r.eval(sc);
         }
      }

      Expr unary() {
         if (this.eat('-')) {
            Expr e = this.unary();
            return (sc) -> -e.eval(sc);
         } else {
            return this.eat('+') ? this.unary() : this.atom();
         }
      }

      Expr atom() {
         this.skip();
         if (this.eat('(')) {
            Expr e = this.sum();
            this.eat(')');
            return e;
         } else {
            int start = this.pos;
            char c = this.pos < this.s.length() ? this.s.charAt(this.pos) : 0;
            if (!Character.isDigit(c) && c != '.') {
               while(this.pos < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '_' || this.s.charAt(this.pos) == '.')) {
                  ++this.pos;
               }

               String name = this.s.substring(start, this.pos);
               if (name.isEmpty()) {
                  throw new IllegalArgumentException("Unexpected " + c);
               } else if (!this.eat('(')) {
                  return Molang.variable(name);
               } else {
                  List<Expr> args = new ArrayList();
                  if (!this.eat(')')) {
                     do {
                        args.add(this.sum());
                     } while(this.eat(','));

                     this.eat(')');
                  }

                  return Molang.function(name, args);
               }
            } else {
               while(this.pos < this.s.length() && (Character.isDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '.')) {
                  ++this.pos;
               }

               return Molang.constant(Double.parseDouble(this.s.substring(start, this.pos)));
            }
         }
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface Expr {
      double eval(Scope var1);
   }
}
