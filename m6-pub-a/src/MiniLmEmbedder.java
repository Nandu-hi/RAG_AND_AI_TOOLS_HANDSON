import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

/**
 * Real semantic embedder: all-MiniLM-L6-v2 run in-process via ONNX
 * (bundled in the langchain4j jar, no network). Produces 384-dim vectors,
 * L2-normalised so a plain dot product is cosine similarity.
 */
public class MiniLmEmbedder implements Embedder {

    public static final int DIM = 384;

    private final AllMiniLmL6V2EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

    @Override
    public float[] embed(String text) {
        float[] v = model.embed(text == null ? "" : text).content().vector();
        double norm = 0.0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        }
        return v;
    }

    @Override
    public String toString() {
        return "MiniLmEmbedder[dim=" + DIM + "]";
    }
}
