package com.sales.smartBusiness.product;

import com.sales.smartBusiness.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One photo of a product. Only the path (see {@code common.ImageStorage}) and content
 * type live here — the bytes are on disk. Belongs to its {@link Product}, which owns the
 * lifecycle: a photo is only ever added or removed through the product.
 */
@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 255)
    private String path;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
}
