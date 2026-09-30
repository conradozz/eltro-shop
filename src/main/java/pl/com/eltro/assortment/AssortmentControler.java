package pl.com.eltro.assortment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assortment")
public class AssortmentControler {

    @Autowired
    AssortmentRepository assortmentRepository;

    @GetMapping("/test")
    public int test (){
        return 1;
    };

    @GetMapping("")
    public List<Assortment> getAll() {
        return assortmentRepository.getAll();
    };

    @GetMapping("/{id}")
    public ResponseEntity<Assortment> getById(@PathVariable int id) {
        Assortment assortment = assortmentRepository.getById(id);

        if (assortment == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(assortment);
    }

    @PostMapping("")
    public int add(@RequestBody Assortment[] assortmentArray) {
        // Zamieniamy tablicę obiektów na listę, aby Twoje repozytorium mogło ją obsłużyć
        java.util.List<Assortment> assortmentList = java.util.Arrays.asList(assortmentArray);
        return assortmentRepository.save(assortmentList);
    }

    @PutMapping("/{id}")
    public int update(@PathVariable("id") int id, @RequestBody Assortment updatedAssortment) {
        // Przypisujemy ID z adresu URL bezpośrednio do przesłanego obiektu
        updatedAssortment.setId(id);

        // Wykonujemy aktualizację w bazie danych i zwracamy wynik (1 jeśli zaktualizowano, 0 jeśli nie znaleziono rekordu)
        return assortmentRepository.update(updatedAssortment);
    }

    @PatchMapping("/{id}")
    public int partiallyUpdate(@PathVariable("id") int id, @RequestBody Assortment updatedAssortment) {
        Assortment assortment = assortmentRepository.getById(id);

        if (assortment != null) {
            if (updatedAssortment.getMachine() != null) assortment.setMachine(updatedAssortment.getMachine());
            if (updatedAssortment.getStockStatus() > 0) assortment.setStockStatus(updatedAssortment.getStockStatus());

            assortmentRepository.update(assortment);

            return 1;
        } else {
            return -1;
        }
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable("id") int id) {
        return assortmentRepository.delete(id);
    }
}
