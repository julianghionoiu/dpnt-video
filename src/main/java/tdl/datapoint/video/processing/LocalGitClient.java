package tdl.datapoint.video.processing;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.nio.file.Path;
import java.util.*;

public class LocalGitClient {

    public static Git init(Path dir) throws GitAPIException {
        return Git.init().setDirectory(dir.toFile()).call();
    }

    public static List<String> getTags(Git git) throws Exception {
        Repository repository = git.getRepository();
        List<Ref> tagRefs = git.tagList().call();
        Map<String, String> commitsToTags = new HashMap<>();

        // Create map of tags
        for (Ref tagRef : tagRefs) {
            String key = repository.peel(tagRef).getPeeledObjectId().getName();
            String value = tagRef.getName().replaceAll("refs/tags/", "");
            commitsToTags.put(key, value);
        }

        // Resolve the commits
        List<String> tags = new ArrayList<>();
        Iterable<RevCommit> commits = git.log().call();
        for (RevCommit commit : commits) {
            String key = commit.getId().name();
            if (commitsToTags.containsKey(key)) {
                tags.add(commitsToTags.get(key));
            }
        }
        Collections.reverse(tags);

        return tags;
    }
}
