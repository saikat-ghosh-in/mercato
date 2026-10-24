export default function PageHeading({ title, description, children }) {
  return <header className="workspace-heading">
    <div><h1>{title}</h1>{description && <p>{description}</p>}</div>
    {children && <div className="workspace-heading-actions">{children}</div>}
  </header>;
}
