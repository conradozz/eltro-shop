package pl.com.eltro.assortment;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class AssortmentRepository {

    @Autowired
    JdbcTemplate jdbcTemplate;

    public List<Assortment> getAll() {
        return jdbcTemplate.query("SELECT ID, Machine, Stock_Status FROM asortment",
                BeanPropertyRowMapper.newInstance(Assortment.class));
    }


    public Assortment getById(int id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT ID, Machine, Stock_Status FROM asortment WHERE ID = ?",
                    BeanPropertyRowMapper.newInstance(Assortment.class),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int save(List<Assortment> assortments) {
        assortments.forEach(assortment -> jdbcTemplate
                .update("INSERT INTO asortment (Machine, Stock_Status) VALUES(?,?) ",
                        assortment.getMachine(), assortment.getStockStatus()
                ));
        return 1;
    }

    public int update(Assortment assortment) {
        return jdbcTemplate.update("UPDATE asortment SET Machine=?, Stock_Status=? WHERE ID =? ",
                assortment.getMachine(), assortment.getStockStatus(), assortment.getId());
    }

    public int delete (int id) {
        return jdbcTemplate.update("DELETE FROM asortment WHERE id=?", id);
    }

}
