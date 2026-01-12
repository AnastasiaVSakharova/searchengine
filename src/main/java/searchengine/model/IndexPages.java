package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "index_pages")
@Getter
@Setter
public class IndexPages {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name="page_id")
    private int pageId;

    @Column(name="lemma_id")
    private int lemmaId;

    @Column(name ="my_rank", columnDefinition = "FLOAT", nullable = false)
    private Double myRank;
}
