package com.amusement.park.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.entity.Show;
import com.amusement.park.repository.ShowRepository;

/**
 * 园区演出业务。
 *
 * <p>核心规则：预订座位不得超过演出容量（capacity），超出则抛出中文
 * {@link BizException} 拒绝，避免超售。</p>
 */
@Service
public class ShowService {

    private final ShowRepository showRepository;

    public ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    public List<Show> list() {
        return showRepository.findAll();
    }

    public Show get(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new BizException("演出不存在：" + id));
    }

    @Transactional
    public Show create(Show input) {
        if (input.getShowCode() == null || input.getShowCode().isBlank()) {
            throw new BizException("演出编号不能为空");
        }
        if (input.getShowName() == null || input.getShowName().isBlank()) {
            throw new BizException("演出名称不能为空");
        }
        if (input.getCapacity() == null || input.getCapacity() <= 0) {
            throw new BizException("演出容量必须大于 0");
        }
        Show show = new Show();
        show.setShowCode(input.getShowCode().trim());
        show.setShowName(input.getShowName().trim());
        show.setVenue(input.getVenue());
        show.setCapacity(input.getCapacity());
        show.setBooked(input.getBooked() == null ? 0 : Math.max(0, input.getBooked()));
        if (show.getBooked() > show.getCapacity()) {
            throw new BizException("初始预订 " + show.getBooked() + " 已超过容量 " + show.getCapacity());
        }
        show.setShowAt(input.getShowAt());
        show.setCreatedAt(LocalDateTime.now());
        return showRepository.save(show);
    }

    @Transactional
    public Show update(Long id, Show input) {
        Show show = get(id);
        if (input.getShowName() != null && !input.getShowName().isBlank()) {
            show.setShowName(input.getShowName().trim());
        }
        if (input.getVenue() != null) {
            show.setVenue(input.getVenue());
        }
        if (input.getCapacity() != null) {
            if (input.getCapacity() <= 0) {
                throw new BizException("演出容量必须大于 0");
            }
            if (show.getBooked() > input.getCapacity()) {
                throw new BizException("容量不能小于已预订座位数（已订 " + show.getBooked() + "）");
            }
            show.setCapacity(input.getCapacity());
        }
        if (input.getShowAt() != null) {
            show.setShowAt(input.getShowAt());
        }
        return showRepository.save(show);
    }

    /**
     * 预订座位。超出容量则拒绝。
     *
     * @param seats 本次预订座位数
     */
    @Transactional
    public Show reserve(Long id, Integer seats) {
        Show show = get(id);
        if (seats == null || seats <= 0) {
            throw new BizException("预订座位数必须大于 0");
        }
        int after = show.getBooked() + seats;
        if (after > show.getCapacity()) {
            throw new BizException("超出演出容量，剩余座位仅 "
                    + (show.getCapacity() - show.getBooked()) + " 个，无法再预订 " + seats + " 个");
        }
        show.setBooked(after);
        return showRepository.save(show);
    }
}
