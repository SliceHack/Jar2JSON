package com.sliceclient;

import lombok.Getter;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Getter
@SuppressWarnings("ResultOfMethodCallIgnored")
public enum Loader {
    INSTANCE;

    private final HashMap<String, JSONObject> jsons = new HashMap<>();
    private final File folder = new File("toJSON");

    Loader() {
        if(!folder.exists()) folder.mkdir();
        File[] files = folder.listFiles();

        Arrays.stream(Objects.requireNonNull(files)).filter(file -> file.getName().endsWith(".jar")).forEach(file -> {
            JSONObject json = new JSONObject();
            try(CustomClassLoader classLoader = new CustomClassLoader(file, getClass().getClassLoader())) {

                // Read the jar file
                JarInputStream jarFile = new JarInputStream(new FileInputStream(file));
                JarEntry jarEntry;
                while (true) {
                    jarEntry = jarFile.getNextJarEntry();
                    if (jarEntry == null) break;

                    if ((jarEntry.getName().endsWith(".class"))) {
                        String className = jarEntry.getName().substring(0, jarEntry.getName().length() - 6);
                        className = className.replace('/', '.');
                        try {
                            Class<?> c = classLoader.loadClass(className);
                            if(c.getSimpleName().isEmpty()) continue;

                            JSONObject classJson = new JSONObject(), fieldsJson = new JSONObject(), methodsJson = new JSONObject(), constructorsJson = new JSONObject();

                            Arrays.stream(c.getDeclaredFields()).forEach((field -> {
                                JSONObject fieldJson = new JSONObject();
                                fieldJson.put("name", field.getName());
                                fieldJson.put("type", field.getType().getSimpleName());
                                fieldJson.put("modifiers", Modifier.toString(field.getModifiers()));
                            }));

                            Arrays.stream(c.getDeclaredMethods()).forEach((method -> {
                                JSONObject methodJson = new JSONObject();
                                methodJson.put("name", method.getName());
                                methodJson.put("returnType", method.getReturnType().getSimpleName());
                                methodJson.put("modifiers", Modifier.toString(method.getModifiers()));

                                AtomicInteger arg = new AtomicInteger();
                                JSONObject argsJson = new JSONObject();
                                Arrays.stream(method.getParameterTypes()).forEach((parameter -> argsJson.put("arg" + arg.getAndIncrement(), parameter.getName())));
                                methodJson.put("args", argsJson);
                                methodsJson.put(method.getName(), methodJson);
                            }));

                            AtomicInteger i = new AtomicInteger();
                            Arrays.stream(c.getDeclaredConstructors()).forEach((constructor -> {
                                JSONObject constructorJson = new JSONObject();
                                constructorJson.put("name", constructor.getName());

                                JSONObject argsJson = new JSONObject();

                                AtomicInteger arg = new AtomicInteger();
                                Arrays.stream(constructor.getParameterTypes()).forEach((parameter -> argsJson.put("arg" + arg.getAndIncrement(), parameter.getName())));
                                constructorJson.put("args", argsJson);
                                constructorsJson.put("constructor" + i.getAndIncrement(), constructorJson);
                            }));

                            classJson.put("constructors", constructorsJson);
                            classJson.put("methods", methodsJson);
                            classJson.put("fields", fieldsJson);
                            json.put(c.getName(), classJson);
                        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {}
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                FileWriter writer = new FileWriter(new File(folder, file.getName().replace(".jar", ".json")));
                writer.write(json.toString());
                writer.flush();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Wrote " + file.getName().replace(".jar", ".json"));
        });
    }


}
