package uz.csec.zirhanalizator;

import android.util.Log;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.StrongConnectivityAlgorithm;
import org.jgrapht.alg.scoring.ClosenessCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphMetrics {

    public static void analyzeGraph(Set<String> methods, List<String[]> edges, String apkPath) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Tugunlarni qo‘shish
        for (String method : methods) {
            graph.addVertex(method);
        }

        // Chaqiruvlarni qo‘shish
        for (String[] edge : edges) {
            if (!graph.containsVertex(edge[0])) graph.addVertex(edge[0]);
            if (!graph.containsVertex(edge[1])) graph.addVertex(edge[1]);
            graph.addEdge(edge[0], edge[1]);
        }

        // Node va edge soni
        int numNodes = graph.vertexSet().size();
        int numEdges = graph.edgeSet().size();

        // O‘rtacha in-degree va out-degree
        double avgInDegree = graph.vertexSet().stream()
                .mapToInt(graph::inDegreeOf)
                .average().orElse(0.0);

        double avgOutDegree = graph.vertexSet().stream()
                .mapToInt(graph::outDegreeOf)
                .average().orElse(0.0);

        // Density
        double density = (numNodes > 1) ? (2.0 * numEdges) / (numNodes * (numNodes - 1)) : 0.0;

        // PageRank va ClosenessCentrality
        PageRank<String, DefaultEdge> pagerank = new PageRank<>(graph);
        ClosenessCentrality<String, DefaultEdge> closeness = new ClosenessCentrality<>(graph);

        // Strongly connected components
        StrongConnectivityAlgorithm<String, DefaultEdge> scc = new KosarajuStrongConnectivityInspector<>(graph);

        // Clustering coefficient (oddiy approx)
        double avgClustering = computeClusteringCoefficient(graph);

        // Natijalarni chiqarish
        Log.d("GraphMetrics_" + apkPath, "num_nodes = " + numNodes);
        Log.d("GraphMetrics_" + apkPath, "num_edges = " + numEdges);
        Log.d("GraphMetrics_" + apkPath, "avg_in_degree = " + avgInDegree);
        Log.d("GraphMetrics_" + apkPath, "avg_out_degree = " + avgOutDegree);
        Log.d("GraphMetrics_" + apkPath, "density = " + density);
        Log.d("GraphMetrics_" + apkPath, "SCC count = " + scc.stronglyConnectedSets().size());
        Log.d("GraphMetrics_" + apkPath, "avg pagerank = " +
                pagerank.getScores().values().stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Log.d("GraphMetrics_" + apkPath, "avg closeness = " +
                closeness.getScores().values().stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Log.d("GraphMetrics_" + apkPath, "avg clustering coefficient = " + avgClustering);
    }

    /**
     * Oddiy undirected clustering coefficient hisoblash
     */
    private static double computeClusteringCoefficient(Graph<String, DefaultEdge> graph) {
        if (graph.vertexSet().isEmpty()) return 0.0;

        double clusteringSum = 0.0;

        for (String v : graph.vertexSet()) {
            Set<String> neighbors = graph.outgoingEdgesOf(v).stream()
                    .map(graph::getEdgeTarget)
                    .collect(java.util.stream.Collectors.toSet());

            int k = neighbors.size();
            if (k < 2) continue;

            int links = 0;
            for (String u1 : neighbors) {
                for (String u2 : neighbors) {
                    if (!u1.equals(u2) && graph.containsEdge(u1, u2)) {
                        links++;
                    }
                }
            }

            clusteringSum += (double) links / (k * (k - 1));
        }

        return clusteringSum / graph.vertexSet().size();
    }
}
