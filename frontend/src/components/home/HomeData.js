
export const HERO_SLIDES = [
  {
    tag: "New Season",
    headline: ["Everything", "you need,", "one place."],
    italicLine: 1,
    sub: "Curated collections from India's best verified sellers",
    cta: "Shop Now",
    ctaSecondary: "New Arrivals",
    ctaSecondaryLink: "/products?sortBy=createdAt&sortingOrder=desc",
    gradient: "from-gray-950 via-gray-900 to-blue-950",
    glowColor: "bg-blue-600/15",
    accentColor: "bg-blue-500",
    textAccent: "text-blue-400",
    link: "/products",
    badge: { value: "2M+", label: "Products" },
  },
  {
    tag: "Best Value",
    headline: ["Premium picks,", "honest", "prices."],
    italicLine: 1,
    sub: "Handpicked products across every category, priced right",
    cta: "Shop Now",
    ctaSecondary: "View All",
    ctaSecondaryLink: "/products",
    gradient: "from-gray-950 via-violet-950 to-gray-900",
    glowColor: "bg-violet-500/12",
    accentColor: "bg-violet-500",
    textAccent: "text-violet-400",
    link: "/products?sortBy=sellingPrice&sortingOrder=desc",
    badge: { value: "500+", label: "Products" },
  },
  {
    tag: "Just In",
    headline: ["Fresh picks", "added this", "week."],
    italicLine: 0,
    sub: "Discover the latest products from our newest sellers",
    cta: "Explore Now",
    ctaSecondary: "Browse All",
    ctaSecondaryLink: "/products",
    gradient: "from-gray-950 via-blue-950 to-gray-950",
    glowColor: "bg-blue-500/12",
    accentColor: "bg-blue-500",
    textAccent: "text-blue-400",
    link: "/products?sortBy=createdAt&sortingOrder=desc",
    badge: { value: "New", label: "This Week" },
  },
];

export const TRUST_BADGES = [
  { icon: "🚚", title: "Free Delivery", sub: "On orders above ₹999" },
  { icon: "↩️", title: "7-Day Returns", sub: "No questions asked" },
  { icon: "🔒", title: "Secure Payments", sub: "Encrypted checkout" },
  { icon: "✨", title: "New Arrivals", sub: "Updated every week" },
];

export const FOOTER_LINKS = [
  {
    title: "Shop",
    links: [
      { label: "All Products", href: "/products" },
      { label: "New Arrivals", href: "/products?sortBy=createdAt&sortingOrder=desc" },
      { label: "Best Sellers", href: "/products?sortBy=sellingPrice&sortingOrder=desc" },
      { label: "Deals", href: "/products?sortBy=discountPercent&sortingOrder=desc" },
    ],
  },
  {
    title: "Help",
    links: [
      { label: "My Account", href: "/profile" },
      { label: "Track Order", href: "/orders" },
      { label: "My Cart", href: "/cart" },
      { label: "Contact Us", href: "#" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "About", href: "#" },
      { label: "Sell on Mercato", href: "/register" },
      { label: "Careers", href: "#" },
      { label: "Privacy Policy", href: "#" },
    ],
  },
];