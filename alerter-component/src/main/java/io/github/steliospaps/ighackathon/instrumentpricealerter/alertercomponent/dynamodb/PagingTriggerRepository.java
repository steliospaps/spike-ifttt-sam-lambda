package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface PagingTriggerRepository extends PagingAndSortingRepository<Trigger, String> {
	//Page<User> findByLastName(String lastName, Pageable pageable);

	@EnableScan
	@EnableScanCount
	Iterable<Trigger> findAll();
	
	@EnableScan
	@EnableScanCount
	Page<Trigger> findAll(Pageable pageable);
}