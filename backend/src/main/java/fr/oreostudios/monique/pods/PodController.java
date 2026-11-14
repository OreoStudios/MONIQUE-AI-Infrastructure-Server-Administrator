package fr.oreostudios.monique.pods;

import fr.oreostudios.monique.pods.PodModels.CreatePod;
import fr.oreostudios.monique.pods.PodModels.PodView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pods")
public class PodController {
    private final PodService pods;

    public PodController(PodService pods) {
        this.pods = pods;
    }

    @GetMapping
    public List<PodView> all() { return pods.list(); }

    @GetMapping("/{id}")
    public PodView one(@PathVariable UUID id) { return pods.find(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PodView create(@Valid @RequestBody CreatePod request) { return pods.create(request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { pods.delete(id); }
}
