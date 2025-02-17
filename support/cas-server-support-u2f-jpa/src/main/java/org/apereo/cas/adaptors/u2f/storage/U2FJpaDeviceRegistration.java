package org.apereo.cas.adaptors.u2f.storage;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;

/**
 * This is {@link U2FJpaDeviceRegistration}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@Getter
@Entity
@Setter
@Table(name = "U2FDeviceRegistration")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class U2FJpaDeviceRegistration extends U2FDeviceRegistration {
    @Serial
    private static final long serialVersionUID = 171500798004450561L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    @Builder.Default
    private long id = -1;
}
