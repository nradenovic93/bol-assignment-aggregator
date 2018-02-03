package com.bol.test.assignment.aggregator;

import java.util.concurrent.ExecutionException;

import com.bol.test.assignment.offer.Offer;
import com.bol.test.assignment.offer.OfferCondition;
import com.bol.test.assignment.offer.OfferService;
import com.bol.test.assignment.order.Order;
import com.bol.test.assignment.order.OrderService;
import com.bol.test.assignment.product.Product;
import com.bol.test.assignment.product.ProductService;
import lombok.Getter;

public class AggregatorService {

    private static int TIME_OUT = 2000;

    private OrderService orderService;
    private OfferService offerService;
    private ProductService productService;

    public AggregatorService(OrderService orderService, OfferService offerService, ProductService productService) {
        this.orderService = orderService;
        this.offerService = offerService;
        this.productService = productService;
    }

    public EnrichedOrder enrich(int sellerId) throws ExecutionException, InterruptedException {
        final Order order = orderService.getOrder(sellerId);

        final FetcherThread fetcherThread = new FetcherThread(order);
        fetcherThread.start();

        try {
            fetcherThread.join(TIME_OUT);
        } catch (InterruptedException e) {
            System.err.println("Thread 'fetcher' interrupted\n");
        }

        final Product product = fetcherThread.getProduct();
        final Offer offer = fetcherThread.getOffer();

        return combine(order, offer, product);
    }

    private EnrichedOrder combine(Order order, Offer offer, Product product) {
        return new EnrichedOrder(order.getId(), offer.getId(), offer.getCondition(), product.getId(), product.getTitle());
    }

    /**
     * New thread for fetching product and offer simultaneously.
     */
    class FetcherThread extends Thread {

        private Order order;

        @Getter
        private Product product;
        @Getter
        private Offer offer;

        protected FetcherThread(final Order order) {
            this.order = order;

            // Set default values.
            product = new Product(-1, null);
            offer = new Offer(-1, OfferCondition.UNKNOWN);
        }

        @Override
        public void run() {
            System.out.println("Running thread 'fetcher' for order id " +  order.getId());

            final Thread productThread = new Thread(() -> {
                System.out.println("    Fetching product with id " +  order.getProductId());
                product = productService.getProduct(order.getProductId());
                System.out.println("    Thread 'product' exiting.");
            });

            final Thread offerThread = new Thread(() -> {
                System.out.println("    Fetching offer with id " +  order.getOfferId());
                offer = offerService.getOffer(order.getOfferId());
                System.out.println("    Thread 'offer' exiting.");
            });

            // Start threads simultaneously.
            productThread.start();
            offerThread.start();

            try {
                // Wait for both threads to complete.
                productThread.join(TIME_OUT);
                offerThread.join(TIME_OUT);
            } catch (InterruptedException e) {
                System.err.println("Thread(s) interrupted");
            }

            System.out.println("Thread 'fetcher' exiting\n");
        }
    }
}