package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.dto.BillDto;
import com.budgetguardian.mapper.BillMapper;
import com.budgetguardian.model.Bill;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.BillRepository;
import com.budgetguardian.repository.StorageException;

/** REST access to {@code /bills}. */
public final class ApiBillRepository implements BillRepository {

    private final HttpJsonClient http;

    public ApiBillRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public HashMap<Long, Bill> findAll() throws StorageException {
        try {
            BillDto[] dtos = http.get("/bills", BillDto[].class);
            HashMap<Long, Bill> bills = new HashMap<>();
            for (BillDto dto : dtos) {
                Bill bill = BillMapper.toModel(dto);
                bills.put(bill.id(), bill);
            }
            return bills;
        } catch (ApiException e) {
            throw new StorageException("Failed to load bills from backend", e);
        }
    }

    @Override
    public Bill insert(Bill bill) throws StorageException {
        try {
            BillDto saved = http.post("/bills", BillMapper.toDto(bill), BillDto.class);
            return bill.withId(saved.id);
        } catch (ApiException e) {
            throw new StorageException("Failed to save bill", e);
        }
    }

    @Override
    public void updateLastPaid(Bill bill) throws StorageException {
        try {
            String lastPaidDate = bill.lastPaidDate() != null ? bill.lastPaidDate().toString() : null;
            http.put("/bills/" + bill.id() + "/last-paid", new LastPaidDto(lastPaidDate), BillDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to update bill " + bill.id(), e);
        }
    }

    @Override
    public void delete(long billId) throws StorageException {
        try {
            http.delete("/bills/" + billId);
        } catch (ApiException e) {
            throw new StorageException("Failed to delete bill " + billId, e);
        }
    }

    /** Body of the last-paid-date update endpoint. */
    private record LastPaidDto(String lastPaidDate) {
    }
}
