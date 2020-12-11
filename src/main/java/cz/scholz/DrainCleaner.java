package cz.scholz;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command
public class DrainCleaner implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(DrainCleaner.class);

    @Inject
    KubernetesClient client;

    @CommandLine.Option(names = {"-r", "--refresh"}, description = "How often to refresh the data and recheck whether pods need to be drained", defaultValue = "30000")
    int refresh;

    void cleanTheDrain()    {
        List<String> nodeNames = client.nodes().list().getItems()
                .stream()
                .filter(node -> node.getSpec() != null && node.getSpec().getUnschedulable() == Boolean.TRUE)
                .map(node -> node.getMetadata().getName())
                .collect(Collectors.toList());

        if (nodeNames.isEmpty()) {
            LOG.info("No unschedulable nodes found. Maybe next time ...");
            return;
        }

        LOG.info("Found unschedulable nodes {} ... lets check if they have any Strimzi pods!", nodeNames);

        List<Pod> pods = client.pods().inAnyNamespace().withLabels(Collections.singletonMap("strimzi.io/kind", "Kafka")).list().getItems();

        for (Pod pod : pods)    {
            if (pod.getMetadata().getLabels() != null
                    && pod.getMetadata().getLabels().get("strimzi.io/name") != null
                    && (pod.getMetadata().getLabels().get("strimzi.io/name").endsWith("kafka") || pod.getMetadata().getLabels().get("strimzi.io/name").endsWith("zookeeper"))) {
                LOG.debug("Checking if pod {} is on an unschedulable node!", pod.getMetadata().getName());

                if (nodeNames.contains(pod.getSpec().getNodeName())) {
                    LOG.info("Pod {} is running on un unschedulable node {} and will be marked for draining", pod.getMetadata().getName(), pod.getSpec().getNodeName());

                    //pod.getMetadata().getAnnotations().put("strimzi.io/manual-rolling-update", "true");
                    //client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).patch(pod);
                    client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).edit()
                            .editMetadata()
                                .addToAnnotations("strimzi.io/manual-rolling-update", "true")
                            .endMetadata()
                            .done();
                }
            }
        }
    }

    @Override
    public void run() {
        client.getConfiguration().setTrustCerts(true);

        try {
            while (true)    {
                cleanTheDrain();
                Thread.sleep(refresh);
            }
        } catch (InterruptedException e) {
            LOG.error("Got interrupted while waiting for the next refresh", e);
            System.exit(1);
        }
    }
}
