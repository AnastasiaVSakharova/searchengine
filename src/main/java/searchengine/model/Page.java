package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name="page",
        indexes = @Index(name = "idx_path", columnList = "path"))
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "path", columnDefinition = "VARCHAR(255)", length = 255, nullable = false)
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name="content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @Column(name="site_id")
    private int siteId;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private Set<IndexPages> indexPagesList;

}
