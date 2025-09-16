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

        // qo‘shimcha hisoblagichlar
        int telephony_hits = 0;
        int sms_hits = 0;
        int network_hits = 0;
        int file_hits = 0;
        int crypto_hits = 0;
        int reflection_hits = 0;

        if (apkFile.getName().endsWith(".dex")) {
            DexBackedDexFile dexFile = DexFileFactory.loadDexFile(apkFile, Opcodes.forApi(19));
            int[] counters = analyzeDexFile(dexFile, methodsSet, edgesList);
            telephony_hits += counters[0];
            sms_hits += counters[1];
            network_hits += counters[2];
            file_hits += counters[3];
            crypto_hits += counters[4];
            reflection_hits += counters[5];
        } else {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(apkFile, Opcodes.forApi(19));

            for (String dexEntry : container.getDexEntryNames()) {
                DexBackedDexFile dexFile = container.getEntry(dexEntry).getDexFile();
                if (dexFile != null) {
                    int[] counters = analyzeDexFile(dexFile, methodsSet, edgesList);
                    telephony_hits += counters[0];
                    sms_hits += counters[1];
                    network_hits += counters[2];
                    file_hits += counters[3];
                    crypto_hits += counters[4];
                    reflection_hits += counters[5];
                }
            }
        }

        String[] methodsArray = methodsSet.toArray(new String[0]);
        String[][] edgesArray = edgesList.toArray(new String[0][]);
        Log.d(TAG, "Analyzed file: " + apkFile.getAbsolutePath());
        Log.d(TAG, "Methods count = " + methodsSet.size());
        Log.d(TAG, "Edges count = " + edgesList.size());
        double[] metrics = nativeLib.computeMetrics(methodsArray, edgesArray);
        String[] metricNames = {
                "Node count", "Edge count", "Density", "Avg In-Degree", "Avg Out-Degree",
                "Avg Closeness", "Avg PageRank", "SCC count", "Clustering coefficient"
        };
        for (int i = 0; i < metrics.length && i < metricNames.length; i++) {
            Log.d(TAG, metricNames[i] + ": " + metrics[i]);
        }

        // qo‘shimcha natijalar
        Log.d(TAG, "telephony_hits: " + telephony_hits);
        Log.d(TAG, "sms_hits: " + sms_hits);
        Log.d(TAG, "network_hits: " + network_hits);
        Log.d(TAG, "file_hits: " + file_hits);
        Log.d(TAG, "crypto_hits: " + crypto_hits);
        Log.d(TAG, "reflection_hits: " + reflection_hits);

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

        methodsSet.clear();
        edgesList.clear();
        System.gc();
    }

    private static int[] analyzeDexFile(DexBackedDexFile dexFile,
                                        Set<String> methods,
                                        List<String[]> edges) {
        int telephony_hits = 0;
        int sms_hits = 0;
        int network_hits = 0;
        int file_hits = 0;
        int crypto_hits = 0;
        int reflection_hits = 0;

        for (ClassDef classDef : dexFile.getClasses()) {
            for (Method method : classDef.getMethods()) {
                String caller = classDef.getType() + "->" + method.getName();
                if (isSystemMethod(caller)) {
                    methods.add(caller);
                    if (method.getImplementation() != null) {
                        for (Instruction insn : method.getImplementation().getInstructions()) {
                            if (insn instanceof ReferenceInstruction) {
                                String ref = ((ReferenceInstruction) insn).getReference().toString();
                                if (ref.contains("->")) {
                                    methods.add(ref);
                                    edges.add(new String[]{caller, ref});

                                    // qo‘shimcha API tekshiruvlar
                                    if (ref.contains("TelephonyManager") || ref.contains("ITelephony")) {
                                        telephony_hits++;
                                    } else if (ref.contains("SmsManager") || ref.contains("ISms")) {
                                        sms_hits++;
                                    } else if (ref.contains("HttpURLConnection") ||
                                            ref.contains("OkHttp") ||
                                            ref.contains("Socket") ||
                                            ref.contains("URLConnection")) {
                                        network_hits++;
                                    } else if (ref.contains("java/io/File") ||
                                            ref.contains("FileInputStream") ||
                                            ref.contains("FileOutputStream")) {
                                        file_hits++;
                                    } else if (ref.contains("javax/crypto") ||
                                            ref.contains("java/security")) {
                                        crypto_hits++;
                                    } else if (ref.contains("java/lang/reflect")) {
                                        reflection_hits++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return new int[]{telephony_hits, sms_hits, network_hits, file_hits, crypto_hits, reflection_hits};
    }

    private static boolean isSystemMethod(String method) {
        return method.startsWith("Ljava/")
                || method.startsWith("Ljavax/")
                || method.startsWith("Landroid/");
    }
}
