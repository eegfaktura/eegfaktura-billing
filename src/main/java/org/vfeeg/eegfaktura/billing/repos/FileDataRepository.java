package org.vfeeg.eegfaktura.billing.repos;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.FileData;


public interface FileDataRepository extends JpaRepository<FileData, UUID> {

    @Modifying
    @Transactional
    @Query(value="DELETE FROM file_data fd WHERE fd.id IN (SELECT bdf.file_data_id FROM " +
            " billing_document_file bdf, billing_document bd WHERE " +
            " bdf.billing_document_id = bd.id AND bd.billing_run_id = :billingRunId)", nativeQuery = true)
    Integer deleteByBillingRunId(@Param("billingRunId") UUID billingRunId);

    /** Name and type without the bytes (mail text file lists). */
    interface FileMeta {
        UUID getId();
        String getName();
        String getMimeType();
    }

    @Query("select f.id as id, f.name as name, f.mimeType as mimeType from FileData f where f.id in :ids")
    List<FileMeta> findMetaByIdIn(@Param("ids") Collection<UUID> ids);

    /** Deletes without loading the bytes first (deleteById reads the entity). */
    @Modifying
    @Transactional
    @Query("delete from FileData f where f.id = :id")
    void deleteWithoutLoading(@Param("id") UUID id);

}