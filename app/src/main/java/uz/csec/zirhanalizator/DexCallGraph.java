package uz.csec.zirhanalizator;

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
import org.json.JSONObject;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.Deque;

import org.tensorflow.lite.Interpreter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// JGraphT imports
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class DexCallGraph {

    private static final String TAG = "DexCallGraph";
    private static final int BATCH_SIZE = 1;
    private static Context appContext;

    private static Interpreter tflite;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        try {
            java.io.InputStream inputStream = appContext.getAssets().open("model.tflite");
            byte[] modelBytes = new byte[inputStream.available()];
            inputStream.read(modelBytes);
            inputStream.close();
            ByteBuffer modelBuffer = ByteBuffer.allocateDirect(modelBytes.length);
            modelBuffer.order(ByteOrder.nativeOrder());
            modelBuffer.put(modelBytes);
            tflite = new Interpreter(modelBuffer);
        } catch (Exception e) {
            tflite = null;
        }
    }

    public static boolean isModelInitialized() {
        return tflite != null;
    }

    public static void runAnalysisBatch(String[] apkPaths) {
        if (tflite == null) {
            return;
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);

        for (String apkPath : apkPaths) {
            if (apkPath.toLowerCase(Locale.US).contains("com.google.android")) continue;

            final File apkFile = new File(apkPath);
            executor.submit(() -> {
                try {
                    boolean virusDetected = runAnalysis(apkFile);
                    if (virusDetected) {
                        Log.d(TAG, "Virus detected in: " + apkFile.getName());
                    }
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

    static boolean runAnalysis(File apkFile) throws Exception {
        if (tflite == null) {
            Log.w(TAG, "Model hali init qilinmagan. Avval init() ni chaqiring!");
            return false;
        }


        Set<String> methodsSet = new HashSet<>();
        List<String[]> edgesList = new ArrayList<>();

        int telephony_hits = 0, sms_hits = 0, network_hits = 0;
        int file_hits = 0, crypto_hits = 0, reflection_hits = 0;

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

        // Java-da graf metrikalarini hisoblash
        double[] metrics = computeGraphMetrics(methodsSet, edgesList);

        int node_count = methodsSet.size();
        int edge_count = edgesList.size();
        int invoke_edges_total = edge_count;


        // Metrics qiymatlarini tekshirish va normalizatsiya qilish
        for (int i = 0; i < metrics.length; i++) {
            if (Double.isNaN(metrics[i]) || Double.isInfinite(metrics[i])) {
                Log.w(TAG, "Invalid metric at index " + i + ": " + metrics[i] + ", setting to 0.0");
                metrics[i] = 0.0;
            }
        }

        // JSON natija
        JSONObject features = new JSONObject();
        features.put("methods", node_count);
        features.put("invoke_edges_total", invoke_edges_total);
        features.put("node_count", node_count);
        features.put("edge_count", edge_count);
        features.put("density", metrics[2]);
        features.put("avg_in_degree", metrics[3]);
        features.put("avg_out_degree", metrics[4]);
        features.put("avg_closeness", metrics[5]);
        features.put("avg_pagerank", metrics[6]);
        features.put("scc_count", metrics[7]);
        features.put("clustering_coeff", metrics[8]);
        features.put("telephony_hits", telephony_hits);
        features.put("sms_hits", sms_hits);
        features.put("network_hits", network_hits);
        features.put("file_hits", file_hits);
        features.put("crypto_hits", crypto_hits);
        features.put("reflection_hits", reflection_hits);
        features.put("label", 0);

        
        // Rule-based virus detection
        boolean isVirusByRules = false;
        String virusReason = "";
        
        if (file_hits > 50) {
            isVirusByRules = true;
            virusReason += "File hits too high (" + file_hits + " > 50). ";
        }
        
        if (crypto_hits > 15) {
            isVirusByRules = true;
            virusReason += "Crypto hits too high (" + crypto_hits + " > 15). ";
        }
        
        if (reflection_hits > 300) {
            isVirusByRules = true;
            virusReason += "Reflection hits too high (" + reflection_hits + " > 300). ";
        }
        
        if (isVirusByRules) {
            features.put("is_virus", true);
            features.put("virus_confidence", 1.0f);
            features.put("virus_reason", virusReason);
            features.put("detection_method", "rule_based");
            
            return true;
        }

        // Input qiymatlarini normalizatsiya qilish - real APK fayllar uchun
        float normalized_node_count = Math.min(node_count / 100.0f, 1.0f); // Max 100 nodes
        float normalized_edge_count = Math.min(invoke_edges_total / 500.0f, 1.0f); // Max 500 edges
        float normalized_telephony = Math.min(telephony_hits / 10.0f, 1.0f); // Max 10 hits
        float normalized_sms = Math.min(sms_hits / 10.0f, 1.0f);
        float normalized_network = Math.min(network_hits / 20.0f, 1.0f);
        float normalized_file = Math.min(file_hits / 20.0f, 1.0f);
        float normalized_crypto = Math.min(crypto_hits / 10.0f, 1.0f);
        float normalized_reflection = Math.min(reflection_hits / 5.0f, 1.0f);

        // Tensor tayyorlash - model kutilgan format (17 features)
        float[] input = new float[]{
                (float) node_count,                    // 0: methods (raw count)
                (float) invoke_edges_total,            // 1: invoke_edges_total (raw count)
                (float) Math.min(metrics[0], 10.0),    // 2: avg_in_degree (max 10)
                (float) Math.min(metrics[1], 10.0),    // 3: avg_out_degree (max 10)
                (float) Math.min(metrics[2], 1.0),     // 4: density (0-1)
                (float) Math.min(metrics[3], 10.0),    // 5: avg_in_degree (duplicate)
                (float) Math.min(metrics[4], 10.0),    // 6: avg_out_degree (duplicate)
                (float) Math.min(metrics[5], 1.0),     // 7: avg_closeness (0-1)
                (float) Math.min(metrics[6], 1.0),     // 8: avg_pagerank (0-1)
                (float) Math.min(metrics[7], 100.0),   // 9: scc_count (max 100)
                (float) Math.min(metrics[8], 1.0),     // 10: clustering_coeff (0-1)
                (float) telephony_hits,                // 11: telephony_hits (raw count)
                (float) sms_hits,                      // 12: sms_hits (raw count)
                (float) network_hits,                  // 13: network_hits (raw count)
                (float) file_hits,                     // 14: file_hits (raw count)
                (float) crypto_hits,                  // 15: crypto_hits (raw count)
                (float) reflection_hits                 // 16: reflection_hits (raw count)
        };

        
        if (input.length != 17) {
            return false;
        }

        for (int i = 0; i < input.length; i++) {
            if (Float.isNaN(input[i]) || Float.isInfinite(input[i])) {
                input[i] = 0.0f;
            }
        }

        
        if (tflite != null) {
            try {
                float[][] inputArray = new float[1][17];
                System.arraycopy(input, 0, inputArray[0], 0, 17);

                float[][] outputArray = new float[1][1];

                if (inputArray.length != 1 || inputArray[0].length != 17) {
                    return false;
                }

                tflite.run(inputArray, outputArray);
                
                float prediction = outputArray[0][0];

                if (prediction < 0.0f) prediction = 0.0f;
                if (prediction > 1.0f) prediction = 1.0f;

                features.put("prediction", prediction);

                float virus_threshold = 0.7f;
                boolean isVirus = prediction > virus_threshold;
                
                if(isVirus){
                    features.put("is_virus", true);
                    features.put("virus_confidence", prediction);
                    features.put("detection_method", "ml_model");
                    return true; // Virus detected
                }else{
                    features.put("is_virus", false);
                    features.put("virus_confidence", prediction);
                    features.put("detection_method", "ml_model");
                    return false; // No virus detected
                }
                
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private static int[] analyzeDexFile(DexBackedDexFile dexFile,
                                        Set<String> methods,
                                        List<String[]> edges) {
        int telephony_hits = 0, sms_hits = 0, network_hits = 0;
        int file_hits = 0, crypto_hits = 0, reflection_hits = 0;

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

    /**
     * Graf metrikalarini Java-da hisoblash
     * Berilgan Java kodini asosida
     */
    private static double[] computeGraphMetrics(Set<String> methodsSet, List<String[]> edgesList) {
        try {
            // Graf qurish
            DefaultDirectedGraph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            Map<String, Integer> edgeCounts = new HashMap<>();

            // Node'larni qo'shish
            for (String method : methodsSet) {
                graph.addVertex(method);
            }

            // Edge'larni qo'shish
            for (String[] edge : edgesList) {
                String caller = edge[0];
                String callee = edge[1];

                graph.addVertex(callee);

                String edgeKey = caller + "->" + callee;
                edgeCounts.put(edgeKey, edgeCounts.getOrDefault(edgeKey, 0) + 1);

                if (!graph.containsEdge(caller, callee)) {
                    graph.addEdge(caller, callee);
                }
            }

            int nodeCount = graph.vertexSet().size();
            int edgeCount = graph.edgeSet().size();

            // Density hisoblash
            double density = 0.0;
            if (nodeCount > 1) {
                density = (double) edgeCount / ((double) nodeCount * (nodeCount - 1));
                if (Double.isNaN(density) || Double.isInfinite(density)) {
                    density = 0.0;
                }
            }

            // O'rtacha in/out degree hisoblash
            double avgIn = 0.0, avgOut = 0.0;
            for (String v : graph.vertexSet()) {
                avgIn += graph.inDegreeOf(v);
                avgOut += graph.outDegreeOf(v);
            }
            avgIn = nodeCount > 0 ? avgIn / nodeCount : 0.0;
            avgOut = nodeCount > 0 ? avgOut / nodeCount : 0.0;
            
            // NaN va infinity qiymatlarni tekshirish
            if (Double.isNaN(avgIn) || Double.isInfinite(avgIn)) avgIn = 0.0;
            if (Double.isNaN(avgOut) || Double.isInfinite(avgOut)) avgOut = 0.0;

            // Closeness centrality hisoblash
            double sumCloseness = 0.0;
            for (String v : graph.vertexSet()) {
                Map<String, Integer> dist = bfsDistances(graph, v);
                int reachable = 0;
                long sumDist = 0;
                for (Map.Entry<String, Integer> e : dist.entrySet()) {
                    if (e.getKey().equals(v)) continue;
                    int d = e.getValue();
                    if (d >= 0) {
                        reachable++;
                        sumDist += d;
                    }
                }
                double closeness = 0.0;
                if (sumDist > 0) closeness = (double) reachable / (double) sumDist;
                if (Double.isNaN(closeness) || Double.isInfinite(closeness)) closeness = 0.0;
                sumCloseness += closeness;
            }
            double avgCloseness = nodeCount > 0 ? sumCloseness / nodeCount : 0.0;
            if (Double.isNaN(avgCloseness) || Double.isInfinite(avgCloseness)) avgCloseness = 0.0;

            // PageRank hisoblash
            Map<String, Double> pageRank = computePageRank(graph, 0.85, 50, 1e-6);
            double avgPagerank = pageRank.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            if (Double.isNaN(avgPagerank) || Double.isInfinite(avgPagerank)) avgPagerank = 0.0;

            // Strongly Connected Components hisoblash
            List<List<String>> sccs = tarjanSCC(graph);
            int sccCount = sccs.size();

            // Clustering coefficient hisoblash
            double avgClustering = computeAvgClusteringCoefficient(graph);
            if (Double.isNaN(avgClustering) || Double.isInfinite(avgClustering)) avgClustering = 0.0;

            Log.d(TAG, "Graph metrics computed - nodes: " + nodeCount + ", edges: " + edgeCount + 
                  ", density: " + density + ", avgIn: " + avgIn + ", avgOut: " + avgOut);

            return new double[]{
                    avgIn,           // 0
                    avgOut,          // 1
                    density,         // 2
                    avgIn,           // 3 (duplicate for compatibility)
                    avgOut,          // 4 (duplicate for compatibility)
                    avgCloseness,    // 5
                    avgPagerank,     // 6
                    sccCount,        // 7
                    avgClustering    // 8
            };

        } catch (Exception e) {
            Log.e(TAG, "Graph metrics computation error: ", e);
            return new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        }
    }

    /**
     * BFS orqali masofalarni hisoblash
     */
    private static Map<String, Integer> bfsDistances(DefaultDirectedGraph<String, DefaultEdge> graph, String source) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> q = new ArrayDeque<>();
        dist.put(source, 0);
        q.add(source);
        while (!q.isEmpty()) {
            String u = q.poll();
            int du = dist.get(u);
            for (DefaultEdge e : graph.outgoingEdgesOf(u)) {
                String v = graph.getEdgeTarget(e);
                if (!dist.containsKey(v)) {
                    dist.put(v, du + 1);
                    q.add(v);
                }
            }
        }
        return dist;
    }

    /**
     * PageRank algoritmi
     */
    private static Map<String, Double> computePageRank(DefaultDirectedGraph<String, DefaultEdge> graph,
                                                       double damping, int maxIter, double tol) {
        int n = graph.vertexSet().size();
        if (n == 0) return Collections.emptyMap();

        Map<String, Double> pr = new HashMap<>();
        Map<String, Double> prNext = new HashMap<>();
        double init = 1.0 / n;
        for (String v : graph.vertexSet()) pr.put(v, init);

        List<String> nodes = new ArrayList<>(graph.vertexSet());
        for (int iter = 0; iter < maxIter; iter++) {
            double diff = 0.0;
            for (String v : nodes) {
                double sum = 0.0;
                for (DefaultEdge e : graph.incomingEdgesOf(v)) {
                    String u = graph.getEdgeSource(e);
                    int outDeg = graph.outDegreeOf(u);
                    if (outDeg > 0) sum += pr.get(u) / outDeg;
                }
                double val = (1.0 - damping) / n + damping * sum;
                prNext.put(v, val);
            }
            for (String v : nodes) diff += Math.abs(prNext.get(v) - pr.get(v));
            for (String v : nodes) pr.put(v, prNext.get(v));
            if (diff < tol) break;
        }
        return pr;
    }

    /**
     * Tarjan algoritmi orqali Strongly Connected Components topish
     */
    private static List<List<String>> tarjanSCC(DefaultDirectedGraph<String, DefaultEdge> graph) {
        List<List<String>> sccs = new ArrayList<>();
        Map<String, Integer> indexMap = new HashMap<>();
        Map<String, Integer> lowlink = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] index = {0};

        for (String v : graph.vertexSet()) {
            if (!indexMap.containsKey(v)) {
                strongconnect(v, graph, indexMap, lowlink, stack, onStack, index, sccs);
            }
        }
        return sccs;
    }

    private static void strongconnect(String v, DefaultDirectedGraph<String, DefaultEdge> graph,
                                     Map<String, Integer> indexMap, Map<String, Integer> lowlink,
                                     Deque<String> stack, Set<String> onStack, int[] index,
                                     List<List<String>> sccs) {
        indexMap.put(v, index[0]);
        lowlink.put(v, index[0]);
        index[0]++;
        stack.push(v);
        onStack.add(v);

        for (DefaultEdge e : graph.outgoingEdgesOf(v)) {
            String w = graph.getEdgeTarget(e);
            if (!indexMap.containsKey(w)) {
                strongconnect(w, graph, indexMap, lowlink, stack, onStack, index, sccs);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), indexMap.get(w)));
            }
        }

        if (lowlink.get(v).equals(indexMap.get(v))) {
            List<String> component = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                component.add(w);
            } while (!w.equals(v));
            sccs.add(component);
        }
    }

    /**
     * O'rtacha clustering coefficient hisoblash
     */
    private static double computeAvgClusteringCoefficient(DefaultDirectedGraph<String, DefaultEdge> digraph) {
        Map<String, Set<String>> neighbors = new HashMap<>();
        for (String v : digraph.vertexSet()) neighbors.put(v, new HashSet<>());
        for (DefaultEdge e : digraph.edgeSet()) {
            String u = digraph.getEdgeSource(e);
            String v = digraph.getEdgeTarget(e);
            neighbors.get(u).add(v);
            neighbors.get(v).add(u); // undirected ko'rinish
        }

        double sumC = 0.0;
        int count = 0;
        for (Map.Entry<String, Set<String>> ent : neighbors.entrySet()) {
            Set<String> nbrs = ent.getValue();
            int deg = nbrs.size();
            if (deg < 2) {
                sumC += 0.0;
                count++;
                continue;
            }
            int links = 0;
            List<String> list = new ArrayList<>(nbrs);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String a = list.get(i), b = list.get(j);
                    if (neighbors.get(a).contains(b)) links++;
                }
            }
            double possible = deg * (deg - 1) / 2.0;
            double c = possible > 0 ? ((double) links / possible) : 0.0;
            sumC += c;
            count++;
        }
        return count > 0 ? sumC / count : 0.0;
    }
}
