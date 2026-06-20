package com.example.note.repository;

import com.example.note.entity.NoteEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends CassandraRepository<NoteEntity, Long> {

    @Query("SELECT * FROM note WHERE status = 'PUBLISHED' LIMIT ?0")
    List<NoteEntity> findPublished(int limit);
}
