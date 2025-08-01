
-- Drop in dependency-safe order
DROP TABLE IF EXISTS public.orders;
DROP TABLE IF EXISTS public.products;
DROP TABLE IF EXISTS public.order_status;

-- order_status table
CREATE TABLE public.order_status (
    id integer PRIMARY KEY,
    name text NOT NULL
);

COMMENT ON COLUMN public.order_status.id IS 'the status ID';
COMMENT ON COLUMN public.order_status.name IS 'the status name';

-- products table
CREATE TABLE public.products (
    id integer PRIMARY KEY,
    name text NOT NULL,
    price numeric(10, 2) NOT NULL
);

COMMENT ON COLUMN public.products.id IS 'the product ID';
COMMENT ON COLUMN public.products.name IS 'the product name';

-- orders table
CREATE TABLE public.orders (
    id uuid PRIMARY KEY,
    product_id integer NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    quantity integer NOT NULL,
    description text NOT NULL,
    status_id integer NOT NULL,
    total_price numeric(10,2) NOT NULL,
    CONSTRAINT fk_orders_product
       FOREIGN KEY (product_id)
           REFERENCES public.products(id)
           ON DELETE CASCADE,
    CONSTRAINT fk_orders_status
       FOREIGN KEY (status_id)
           REFERENCES public.order_status(id)
           ON DELETE CASCADE
);

COMMENT ON COLUMN public.orders.id IS 'the order ID';
COMMENT ON COLUMN public.orders.product_id IS 'the ID of the product ordered';
COMMENT ON COLUMN public.orders.created_at IS 'the order creation timestamp';
COMMENT ON COLUMN public.orders.updated_at IS 'the order last update timestamp';
COMMENT ON COLUMN public.orders.quantity IS 'the quantity ordered';
COMMENT ON COLUMN public.orders.description IS 'a free form description';
COMMENT ON COLUMN public.orders.status_id IS 'the order status';

-- indexes
CREATE INDEX idx_orders_product_id ON public.orders(product_id);
CREATE INDEX idx_orders_status_id ON public.orders(status_id);

INSERT INTO public.order_status (id, name) VALUES
    (1, 'pending'),
    (2, 'completed');

INSERT INTO public.products (id, name, price) VALUES
    (1, 'laptop', 1299.99),
    (2, 'mouse', 12.00),
    (3, 'keyboard', 23.50),
    (4, 'hub', 18.00),
    (5, 'hdmi_cable', 15.99),
    (6, 'monitor', 229.99);
