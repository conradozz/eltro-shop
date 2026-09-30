package pl.com.eltro.assortment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Assortment {

    @JsonProperty("ID")
    private int id;

    @JsonProperty("Machine")
    private String machine;

    @JsonProperty("Stock_Status")
    private int stockStatus;
}
