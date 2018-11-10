/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jrd.backend.data;

import org.jrd.backend.core.AgentRequestAction;
import org.jrd.backend.core.OutputController;
import org.jrd.backend.core.VmDecompilerStatus;
import org.jrd.backend.decompiling.DecompilerWrapperInformation;
import org.jrd.backend.decompiling.PluginManager;
import org.jrd.frontend.MainFrameView;
import org.jrd.frontend.VmDecompilerInformationController;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @author pmikova
 */
public class Main {

    private static final String VERBOSE = "-verbose";
    private static final String LISTJVMS = "-listjvms";
    private static final String LISTPLUGINS = "-listplugins";
    private static final String LISTCLASSES = "-listclasses";
    private static final String BASE64 = "-base64bytes";
    private static final String BYTES = "-bytes";
    private static final String DECOMPILE = "-decompile";

    public static void main(String[] allargs) throws Exception {
        //copy of alla rgs after verbose check
        List<String> args = new ArrayList(allargs.length);
        Config configureAgent = Config.getConfig();
        VmManager manager = new VmManager();
        for(String arg: allargs){
            String aarg = arg.replaceAll("^--*", "-").toLowerCase();
            if (aarg.equals(VERBOSE)){
                OutputController.getLogger().setVerbose();
            } else {
                args.add(arg);
            }
        }
        if (args.isEmpty()) {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("com.sun.java.swing.plaf.gtk.GTKLookAndFeel".equals(info.getClassName())) {
                    try {
                        javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                        OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, e);
                    }
                    break;
                }
            }
            MainFrameView mainView = new MainFrameView();
            VmDecompilerInformationController controller = new VmDecompilerInformationController(mainView, manager);
        } else {
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                arg = arg.replaceAll("^--*", "-").toLowerCase();
                if (arg.equals(LISTJVMS)) {
                    if (args.size() != 1) {
                        throw new RuntimeException(LISTJVMS + " do not expect argument");
                    }
                    for (VmInfo vm : manager.getVmInfoList()) {
                        System.out.println(vm.getVmPid() + " " + vm.getVmName());
                    }
                    break;
                }
                if (arg.equals(LISTPLUGINS)) {
                    if (args.size() != 1) {
                        throw new RuntimeException(LISTPLUGINS + " do not expect argument");
                    }
                    PluginManager pm = new PluginManager();
                    List<DecompilerWrapperInformation> wrappers = pm.getWrappers();
                    for (DecompilerWrapperInformation dw : wrappers) {
                        System.out.println(dw.getName() + " " + dw.getScope() + "/" + invalidityToString(dw.isInvalidWrapper()) + " - " + dw.getFileLocation());
                    }
                    break;
                } else if (arg.equals(LISTCLASSES)) {
                    if (args.size() != 2) {
                        throw new RuntimeException(LISTCLASSES + " expect exactly one argument - pid or url");
                    }
                    String param = args.get(i + 1);
                    try {
                        VmInfo vmInfo = manager.findVmFromPID(param);
                        AgentRequestAction request = VmDecompilerInformationController.createRequest(vmInfo, null, AgentRequestAction.RequestAction.CLASSES);
                        String response = VmDecompilerInformationController.submitRequest(manager, request);
                        if (response.equals("ok")) {
                            VmDecompilerStatus vmStatus = vmInfo.getVmDecompilerStatus();
                            String[] classes = vmStatus.getLoadedClassNames();
                            for (String clazz : classes) {
                                System.out.println(clazz);
                            }
                        }
                        if (response.equals("error")) {
                            throw new RuntimeException(VmDecompilerInformationController.CLASSES_NOPE);

                        }
                    } catch (NumberFormatException e) {
                        try {
                            URL u = new URL(param);
                            throw new RuntimeException("Remote VM not yet implemented");
                        } catch (MalformedURLException ee) {
                            throw new RuntimeException("Second param was supposed to be URL or PID", ee);
                        }
                    }
                    break;
                } else if (arg.equals(BYTES) || arg.equals(BASE64)) {
                    if (args.size() != 3) {
                        throw new RuntimeException(BYTES + " and " + BASE64 + " expect exactly two argument - pid or url of JVM and fully classified class name");
                    }
                    String jvmStr = args.get(i + 1);
                    String classStr = args.get(i + 2);
                    try {
                        VmInfo vmInfo = manager.findVmFromPID(jvmStr);
                        VmDecompilerStatus result = obtainClass(vmInfo, classStr, manager);
                        if (arg.equals(BYTES)) {
                            byte[] ba = Base64.getDecoder().decode(result.getLoadedClassBytes());
                            System.out.write(ba);
                        } else if (arg.equals(BASE64)) {
                            System.out.println(result.getLoadedClassBytes());
                        } else {
                            throw new RuntimeException("Moon had fallen to Earth and Sun burned the rest...");
                        }
                    } catch (NumberFormatException e) {
                        try {
                            URL u = new URL(jvmStr);
                            throw new RuntimeException("Remote VM not yet implemented");
                        } catch (MalformedURLException ee) {
                            throw new RuntimeException("Second param was supposed to be URL or PID", ee);
                        }
                    }
                    break;
                } else if (arg.equals(DECOMPILE)) {
                    if (args.size() != 4) {
                        throw new RuntimeException(DECOMPILE + " expect exactly three argument - pid or url of JVM, fully classified class name and decompiler name (as set-up) or decompiler json file");
                    }
                    String jvmStr = args.get(i + 1);
                    String classStr = args.get(i + 2);
                    String decompilerName = args.get(i + 3);
                    try {
                        VmInfo vmInfo = manager.findVmFromPID(jvmStr);
                        VmDecompilerStatus result = obtainClass(vmInfo, classStr, manager);
                        byte[] bytes = Base64.getDecoder().decode(result.getLoadedClassBytes());
                        PluginManager pluginManager = new PluginManager();
                        if (new File(decompilerName).exists() && decompilerName.toLowerCase().endsWith(".json")){
                            throw new RuntimeException("Plugins laoded directly form file are noty impelemnetd");
                        }
                        List<DecompilerWrapperInformation> wrappers = pluginManager.getWrappers();
                        DecompilerWrapperInformation decompiler = null;
                        for (DecompilerWrapperInformation dw : wrappers) {
                            if (!dw.getScope().equals(DecompilerWrapperInformation.LOCAL_SCOPE) && dw.getName().equals(decompilerName)) {
                                decompiler = dw;
                            }
                        }
                        //LOCAL is preffered one
                        for (DecompilerWrapperInformation dw : wrappers) {
                            if (dw.getScope().equals(DecompilerWrapperInformation.LOCAL_SCOPE) && dw.getName().equals(decompilerName)) {
                                decompiler = dw;
                            }
                        }
                        if (decompiler != null) {
                            String decompiledClass = pluginManager.decompile(decompiler, bytes);
                            System.out.println(decompiledClass);
                        } else {
                            throw new RuntimeException("Decompiler " + decompilerName + " not found");
                        }
                    } catch (NumberFormatException e) {
                        try {
                            URL u = new URL(jvmStr);
                            throw new RuntimeException("Remote VM not yet implemented");
                        } catch (MalformedURLException ee) {
                            throw new RuntimeException("Second param was supposed to be URL or PID", ee);
                        }
                    }
                    break;
                } else {
                    throw new RuntimeException("Unknown commandline switch " + arg + ". Allowed are: " + LISTJVMS + " , " + LISTPLUGINS + " , " + LISTCLASSES + ", " + BASE64 + " , " + BYTES + ", " + DECOMPILE);
                }
            }
        }

    }

    private static String invalidityToString(boolean invalidWrapper) {
        if (invalidWrapper) {
            return "invalid";
        } else {
            return "valid";
        }
    }

    private static VmDecompilerStatus obtainClass(VmInfo vmInfo, String clazz, VmManager manager) {
        AgentRequestAction request = VmDecompilerInformationController.createRequest(vmInfo, clazz, AgentRequestAction.RequestAction.BYTES);
        String response = VmDecompilerInformationController.submitRequest(manager, request);
        if (response.equals("ok")) {
            return vmInfo.getVmDecompilerStatus();
        } else {
            throw new RuntimeException(VmDecompilerInformationController.CLASSES_NOPE);

        }
    }


    // possibly some triggers to periodicaly refresh items in vm

}