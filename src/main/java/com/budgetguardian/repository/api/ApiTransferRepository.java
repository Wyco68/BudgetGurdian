package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.dto.TransferDto;
import com.budgetguardian.mapper.TransferMapper;
import com.budgetguardian.model.Transfer;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.TransferRepository;

/** REST access to {@code /transfers}. */
public final class ApiTransferRepository implements TransferRepository {

    private final HttpJsonClient http;

    public ApiTransferRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public DoublyLinkedList<Transfer> findAll() throws StorageException {
        try {
            TransferDto[] dtos = http.get("/transfers", TransferDto[].class);
            DoublyLinkedList<Transfer> transfers = new DoublyLinkedList<>();
            for (TransferDto dto : dtos) {
                transfers.addLast(TransferMapper.toModel(dto));
            }
            return transfers;
        } catch (ApiException e) {
            throw new StorageException("Failed to load transfers from backend", e);
        }
    }

    @Override
    public Transfer insert(Transfer transfer) throws StorageException {
        try {
            TransferDto saved = http.post("/transfers", TransferMapper.toDto(transfer), TransferDto.class);
            return transfer.withId(saved.id);
        } catch (ApiException e) {
            throw new StorageException("Failed to save transfer", e);
        }
    }

    @Override
    public void delete(long id) throws StorageException {
        try {
            http.delete("/transfers/" + id);
        } catch (ApiException e) {
            throw new StorageException("Failed to delete transfer " + id, e);
        }
    }
}
