package com.example.note.repository;

import com.example.note.entity.CommentEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends CassandraRepository<CommentEntity, Long> {

    @Query("SELECT * FROM comment WHERE note_id = ?0")
    List<CommentEntity> findByNoteId(Long noteId);
}
