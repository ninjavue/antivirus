package uz.csec.antivirus;

import android.content.Context;
import android.util.Log;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DexCallGraph {

    private static final String TAG = "DexCallGraph";
    private static final int BATCH_SIZE = 1; // parallel APK count
    private static Context appContext;       // ilova konteksti

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void runAnalysisBatch(String[] apkPaths) {
        ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);

        for (String apkPath : apkPaths) {
            if (apkPath.toLowerCase(Locale.US).contains("com.google.android")) continue;

            final File apkFile = new File(apkPath);
            executor.submit(() -> {
                try {
                    runAnalysis(apkFile);
                } catch (Exception e) {
                    Log.e(TAG, "Error analyzing " + apkFile.getName(), e);
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
    }

    static void runAnalysis(File apkFile) throws Exception {
        NativeLib nativeLib = new NativeLib();

        Set<String> methodsSet = new HashSet<>();
        List<String[]> edgesList = new ArrayList<>();

        if (apkFile.getName().endsWith(".dex")) {
            DexBackedDexFile dexFile = DexFileFactory.loadDexFile(apkFile, Opcodes.forApi(19));
            analyzeDexFile(dexFile, methodsSet, edgesList);
        } else {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(apkFile, Opcodes.forApi(19));

            for (String dexEntry : container.getDexEntryNames()) {
                DexBackedDexFile dexFile = container.getEntry(dexEntry).getDexFile();
                if (dexFile != null) {
                    analyzeDexFile(dexFile, methodsSet, edgesList);
                }
            }
        }

        Log.d(TAG, "Analyzed file: " + apkFile.getAbsolutePath());
        Log.d(TAG, "Methods count = " + methodsSet.size());
        Log.d(TAG, "Edges count = " + edgesList.size());

        String[] methodsArray = methodsSet.toArray(new String[0]);
        String[][] edgesArray = edgesList.toArray(new String[0][]);
//        Log.d("EdgesList", Arrays.toString(edgesArray));

        // Metrics (chiqarib qo‘yamiz)
        double[] metrics = nativeLib.computeMetrics(methodsArray, edgesArray);
        String[] metricNames = {
                "Node count", "Edge count", "Density", "Avg In-Degree", "Avg Out-Degree",
                "Avg Closeness", "Avg PageRank", "SCC count", "Clustering coefficient"
        };
        for (int i = 0; i < metrics.length && i < metricNames.length; i++) {
            Log.d(TAG, metricNames[i] + ": " + metrics[i]);
        }

        // --- JSON faqat edges ---
        Map<String, Integer> methodToId = new HashMap<>();
        int id = 0;
        for (String m : methodsArray) {
            methodToId.put(m, id++);
        }

        JSONArray edgesJson = new JSONArray();
        for (String[] edge : edgesArray) {
            Integer src = methodToId.get(edge[0]);
            Integer dst = methodToId.get(edge[1]);
            if (src != null && dst != null) {
                JSONObject obj = new JSONObject();
                obj.put("src", src);
                obj.put("dst", dst);
                edgesJson.put(obj);
            }
        }

        JSONObject edgesWrapper = new JSONObject();
        edgesWrapper.put("edges", edgesJson);

        // --- Uzun loglarni bo‘lib chiqaramiz ---
        String jsonString = edgesWrapper.toString();
        int maxLogSize = 3000;
        for (int i = 0; i <= jsonString.length() / maxLogSize; i++) {
            int start = i * maxLogSize;
            int end = Math.min((i + 1) * maxLogSize, jsonString.length());
            Log.d(TAG, "Edges JSON part " + i + ": " + jsonString.substring(start, end));
        }

        methodsSet.clear();
        edgesList.clear();
        System.gc();
    }

    private static void analyzeDexFile(DexBackedDexFile dexFile,
                                       Set<String> methods,
                                       List<String[]> edges) {
        for (ClassDef classDef : dexFile.getClasses()) {
            for (Method method : classDef.getMethods()) {
                String caller = classDef.getType() + "->" + method.getName();
                if (isSystemMethod(caller)) {
                    Log.d("Caller", caller);
                    methods.add(caller);
                    if (method.getImplementation() != null) {
                        for (Instruction insn : method.getImplementation().getInstructions()) {
                            if (insn instanceof ReferenceInstruction) {
                                String ref = ((ReferenceInstruction) insn).getReference().toString();
                                if (ref.contains("->")) {
                                    methods.add(ref);
                                    edges.add(new String[]{caller, ref});
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isSystemMethod(String method) {
        return method.startsWith("Ljava/")
                || method.startsWith("Ljavax/")
                || method.startsWith("Landroid/");
    }
}
