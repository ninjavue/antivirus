//
// Created by zirh-mobil on 9/2/25.
//
#include <jni.h>
#include <vector>
#include <string>
#include <unordered_map>
#include <numeric>
#include <queue>
#include <cmath>

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_uz_csec_zirhanalizator_NativeLib_computeMetrics(
        JNIEnv *env, jobject,
        jobjectArray jMethods,
        jobjectArray jEdges) {

    // --------- STEP 1: Convert Java arrays to C++ -----------------
    jsize numMethods = env->GetArrayLength(jMethods);
    jsize numEdges = env->GetArrayLength(jEdges);

    std::vector<std::string> methods;
    methods.reserve(numMethods);

    for (int i = 0; i < numMethods; i++) {
        jstring str = (jstring) env->GetObjectArrayElement(jMethods, i);
        const char* raw = env->GetStringUTFChars(str, 0);
        methods.emplace_back(raw);
        env->ReleaseStringUTFChars(str, raw);
        env->DeleteLocalRef(str);
    }

    std::unordered_map<std::string, int> indexMap;
    for (int i = 0; i < methods.size(); i++) {
        indexMap[methods[i]] = i;
    }

    // --------- STEP 2: Build adjacency list -----------------
    std::vector<std::vector<int>> adj(numMethods);
    std::vector<int> indeg(numMethods, 0);
    std::vector<int> outdeg(numMethods, 0);

    for (int i = 0; i < numEdges; i++) {
        jobjectArray edgePair = (jobjectArray) env->GetObjectArrayElement(jEdges, i);
        jstring srcStr = (jstring) env->GetObjectArrayElement(edgePair, 0);
        jstring dstStr = (jstring) env->GetObjectArrayElement(edgePair, 1);

        const char* rawSrc = env->GetStringUTFChars(srcStr, 0);
        const char* rawDst = env->GetStringUTFChars(dstStr, 0);

        int u = indexMap[rawSrc];
        int v = indexMap[rawDst];

        adj[u].push_back(v);
        outdeg[u]++;
        indeg[v]++;

        env->ReleaseStringUTFChars(srcStr, rawSrc);
        env->ReleaseStringUTFChars(dstStr, rawDst);
        env->DeleteLocalRef(srcStr);
        env->DeleteLocalRef(dstStr);
        env->DeleteLocalRef(edgePair);
    }

    int n = numMethods;
    int m = numEdges;
    double density = (n > 1) ? (double)m / (n*(n-1)) : 0.0;
    double avg_in = std::accumulate(indeg.begin(), indeg.end(), 0.0) / n;
    double avg_out = std::accumulate(outdeg.begin(), outdeg.end(), 0.0) / n;

    // --------- STEP 3: Approximate PageRank -----------------
    std::vector<double> pr(n, 1.0/n);
    double d = 0.85;
    int iter = 20;
    for (int it = 0; it < iter; it++) {
        std::vector<double> new_pr(n, (1.0-d)/n);
        for (int u = 0; u < n; u++) {
            for (int v : adj[u]) {
                if (outdeg[u] > 0)
                    new_pr[v] += d * pr[u]/outdeg[u];
            }
        }
        pr = new_pr;
    }
    double avg_pr = std::accumulate(pr.begin(), pr.end(), 0.0) / n;

    // --------- STEP 4: Closeness -----------------
    std::vector<double> closeness(n, 0.0);
    for (int u = 0; u < n; u++) {
        std::vector<int> dist(n, -1);
        std::queue<int> q;
        q.push(u);
        dist[u] = 0;
        while(!q.empty()) {
            int cur = q.front(); q.pop();
            for(int v : adj[cur]) {
                if(dist[v] == -1) {
                    dist[v] = dist[cur] + 1;
                    q.push(v);
                }
            }
        }
        double sum = 0;
        int reachable = 0;
        for(int d : dist) {
            if(d>0) { sum += d; reachable++; }
        }
        closeness[u] = (sum>0) ? ((double)reachable / sum) : 0;
    }
    double avg_close = std::accumulate(closeness.begin(), closeness.end(), 0.0) / n;

    // --------- STEP 5: Strongly Connected Components (Kosaraju) -----------------
    std::vector<bool> visited(n,false);
    std::vector<int> order;
    std::function<void(int)> dfs1 = [&](int u){
        visited[u]=true;
        for(int v: adj[u]) if(!visited[v]) dfs1(v);
        order.push_back(u);
    };
    for(int i=0;i<n;i++) if(!visited[i]) dfs1(i);

    std::vector<std::vector<int>> radj(n);
    for(int u=0;u<n;u++) for(int v: adj[u]) radj[v].push_back(u);

    std::fill(visited.begin(),visited.end(),false);
    int scc_count=0;
    std::function<void(int)> dfs2 = [&](int u){
        visited[u]=true;
        for(int v: radj[u]) if(!visited[v]) dfs2(v);
    };
    for(int i=n-1;i>=0;i--){
        int u = order[i];
        if(!visited[u]){
            dfs2(u);
            scc_count++;
        }
    }

    // --------- STEP 6: Clustering coefficient -----------------
    double clustering = 0;
    for(int u=0; u<n; u++){
        int k = adj[u].size();
        if(k<2) continue;
        int links = 0;
        for(int i=0;i<k;i++)
            for(int j=i+1;j<k;j++){
                int a = adj[u][i], b = adj[u][j];
                for(int v: adj[a]) if(v==b) links++;
                for(int v: adj[b]) if(v==a) links++;
            }
        clustering += (double)links / (k*(k-1));
    }
    clustering /= n;

    // --------- STEP 7: Pack results -----------------
    jdoubleArray result = env->NewDoubleArray(9);
    jdouble out[9] = { (double)n, (double)m, density, avg_in, avg_out, avg_close, avg_pr, (double)scc_count, clustering };
    env->SetDoubleArrayRegion(result, 0, 9, out);

    return result;
}
